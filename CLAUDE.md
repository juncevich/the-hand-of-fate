# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**The Hand of Fate** — a voting/selection application with fair rotation support. Users create votes, invite participants by email or define a list of named options, and let a random draw choose a winner. The "Fair Rotation" mode ensures every participant/option wins once per round before anyone wins again.

## Monorepo Structure

```
backend/     Kotlin 2.3.21 + Spring Boot 4.0.6, PostgreSQL, gRPC server
frontend/    React 19 + TypeScript 6 + Vite 8 + Tailwind CSS 4 + shadcn/ui
bot/         Go 1.25.0 Telegram bot, gRPC client to backend
proto/       Shared protobuf definitions (proto/fate/v1/fate.proto)
infra/
  nginx/     Nginx reverse proxy configs
  monitoring/ OTel Collector, Loki, Mimir, Grafana provisioning
  k8s/       Kubernetes manifests + kustomize overlays (staging/production, not active)
.github/
  workflows/ backend.yml + frontend.yml + bot.yml + deploy.yml + server-setup.yml
```

## Common Commands

### Local dev (full stack)

**Option 1 — everything in Docker:**
```bash
cp .env.example .env          # add BOT_TOKEN
docker compose up -d
```

**Option 2 — infrastructure in Docker, apps native (hot-reload):**
```bash
cp .env.example .env          # add BOT_TOKEN
./dev-start.sh                # starts infra via docker-compose.infra.yml, then runs backend/frontend/bot natively
./dev-stop.sh                 # stop infrastructure
# or via make:
make dev-local                # same as dev-start.sh
make infra                    # start only postgres + mailhog
make infra-down               # stop infrastructure
```
- `docker-compose.infra.yml` — lightweight compose file with only postgres and mailhog (used by `dev-start.sh`)
- `dev-start.sh` handles stale process cleanup, colored log output per service, conditional bot startup (skipped if `BOT_TOKEN` unset)

- Frontend:  http://localhost:3000
- Backend:   http://localhost:8080
- Swagger:   http://localhost:8080/swagger-ui.html
- MailHog:   http://localhost:8025
- Grafana:   http://localhost:3001  (admin/admin)

**Default demo user** (seeded by migration V7): `admin@admin.com` / `admin`

### Backend (Kotlin + Gradle)
```bash
cd backend
./gradlew bootRun                # run locally (needs postgres on :5432)
./gradlew test                   # all tests
./gradlew test --tests "*.DrawServiceTest"  # single test class
./gradlew generateProto          # regenerate gRPC stubs from proto/
./gradlew bootJar                # build fat JAR
```

### Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev          # dev server on :3000 (proxies /api → :8080)
npm run dev:mock     # dev server with mock API (no backend needed)
npm test             # run vitest once
npm run test:watch   # vitest in watch mode
npm run lint         # ESLint check
npm run build        # production build
```

### Proto (gRPC stubs)
```bash
# From repo root — regenerate stubs for both components:
make proto              # runs proto-bot + proto-backend
make proto-bot          # bot Go stubs via Buf (requires protoc-gen-go + protoc-gen-go-grpc)
make proto-backend      # backend Java/Kotlin stubs via Gradle

# Install Go proto plugins (needed for make proto-bot):
make install-proto-tools
```

### Bot (Go)
```bash
cd bot
go run ./cmd/bot           # run
go test ./...              # test
go build -o fate-bot ./cmd/bot  # build binary
```

**Direct dependencies** (from `bot/go.mod`):

| Package                            | Version  | Purpose       |
|------------------------------------|----------|---------------|
| `telegram-bot-api/telegram-bot-api/v5` | v5.5.1  | Telegram API  |
| `spf13/viper`                      | v1.21.0  | Config        |
| `go.uber.org/zap`                  | v1.27.1  | Logging       |
| `google.golang.org/grpc`           | v1.80.0  | gRPC client   |
| `google.golang.org/protobuf`       | v1.36.11 | Proto runtime |

### Telegram Bot Commands
| Command | Description |
|---|---|
| `/start`, `/help` | Welcome message and command list |
| `/link <token>` | Link Telegram to app account (token from Settings page) |
| `/votes` | List your votes with status and participant count |
| `/newvote <title> \| <emails> \| <mode> [| <options>]` | Create a new vote; `<emails>` — comma-separated, `<mode>` — `simple`/`fair`, `<options>` — optional comma-separated named options |
| `/draw <id>` | Perform a draw for a vote (creator only) |
| `/result <id>` | Show last draw result for a vote |
| `/unlink` | Unlink Telegram account |

## Architecture

### Auth Flow
- Registration/login returns `accessToken` (JWT, 15 min) + `refreshToken` (UUID, 30 days)
- `refreshToken` is stored hashed in `refresh_tokens` table
- Frontend keeps `accessToken` in Zustand (memory only); `refreshToken` is sent via JSON body; all requests use `withCredentials: true`
- On app mount, `authApi.silentRefresh()` attempts to restore session via httpOnly cookie (`withCredentials: true`, empty body); failure is silently ignored (user stays logged out)
- On 401, Axios interceptor silently calls `/api/v1/auth/refresh`, queues concurrent requests, and retries once

### Frontend State & Data Fetching
- **Zustand** manages auth state (`authStore`) and dark/light theme (`themeStore`; persisted to localStorage)
- **React Query** (`@tanstack/react-query`) handles all server state — queries, mutations, cache invalidation
- Custom Axios instance in `frontend/src/api/client.ts` handles token refresh with a retry queue so concurrent 401s only trigger one refresh call

### Vote Modes
- **SIMPLE**: random draw from all participants or options, no history tracking
- **FAIR_ROTATION**: tracks `draw_history` per round; only participants/options who haven't won in `currentRound` are eligible. When all have won, `currentRound` increments and the cycle restarts

### Vote Draw Targets
A vote can draw from two mutually exclusive sources — if options exist they take precedence:
- **Participants** (default): draw winner from invited participants by email
- **Options**: named entries (e.g., tasks, topics) stored in `vote_options` table; draw picks one option. Options are created at vote creation time or added/removed individually via REST API (`POST /{id}/options`, `DELETE /{id}/options/{optionId}`)

`DrawWinner` is a sealed class with two subtypes: `Participant` and `Option`. `DrawHistory` stores the winner as either `winnerEmail` / `winnerDisplayName` (for participants) or `winnerOption` / `winnerOptionTitle` (for options — denormalized title for resilience to deletion).

### Key Backend Services
- **DrawService**: core draw logic with SIMPLE / FAIR_ROTATION branching for both participants and options; picks draw target automatically (options if any exist, otherwise participants)
- **VoteService**: CRUD for votes including creating `VoteOption` entities from request; `addOption()` / `removeOption()` for post-creation management
- **NotificationService**: async dispatcher — triggers email after draws/invitations, swallows errors so draw success is never blocked by notification failure
- **EmailService**: sends styled HTML emails (dark theme) for vote invitations and draw results
- **FateGrpcService**: gRPC server implementation; uses `runCatching` + `StatusRuntimeException` for error mapping; maps `VoteOptionInfo` proto messages for options

### gRPC (Backend ↔ Bot)
- Proto source: `proto/fate/v1/fate.proto` (package `fate.v1`)
- Buf manages proto (`buf.yaml` + `buf.gen.yaml` at repo root)
- Backend generates Java/Kotlin stubs via `com.google.protobuf` Gradle plugin → `backend/build/generated/source/proto/main/`
- Bot generates Go stubs via Buf → `bot/gen/fate/v1/`
- `FateGrpcService.kt` implements the service; `bot/internal/grpcclient/` wraps the Go stub
- **Key proto additions for vote options:**
  - `VoteOptionInfo { option_id, title }` — returned in `GetVoteDetailsResponse.options`
  - `CreateVoteRequest.options` — repeated string, optional; creates options server-side
  - `DrawVoteResponse.winner_option_title` / `DrawResultInfo.winner_option_title` — winner label when draw picks an option
  - Bot displays winner as: option title → display name → email (priority order)

### Telegram Bot Linking
1. User opens Settings page → clicks "Get link token" → `GET /api/v1/telegram/link-token`
2. Backend creates a `telegram_link_tokens` record (5-min expiry), returns the token
3. User sends `/link <token>` to the bot
4. Bot calls `LinkTelegramAccount` gRPC → backend validates token, sets `users.telegram_id`
5. Bot notifies via Telegram; backend sends emails for invitations and draw results

### Testing
- **Backend**: MockK for unit tests. TestContainers dependency is declared but no integration tests exist yet. Tests live in `backend/src/test/kotlin/`
- **Frontend**: Vitest + `@testing-library/react` + jest-dom

#### Backend Testing Patterns
- Test files mirror main source layout: `service/`, `web/auth/`, `web/vote/`, `grpc/`, `security/`
- All service tests are pure unit tests using MockK — no Spring context needed
- Controller tests use `MockMvcBuilders.standaloneSetup()` — Spring Boot 4.0 removed `@WebMvcTest`
  - Register `AuthenticationPrincipalArgumentResolver` and `PageableHandlerMethodArgumentResolver` explicitly
  - Set `SecurityContextHolder` directly in `@BeforeEach` for endpoints that use `@AuthenticationPrincipal`
  - Always include all required non-null JSON fields (Kotlin default params don't apply at JSON level with Jackson 3.x)
  - `PageImpl` must be constructed with `PageImpl(content, pageable, total)` — single-arg constructor causes serialization error
- `ErrorHandler` generic 500 handler masks exception details from the response — tests check for `"Internal server error"` title, not the original message

#### Frontend Testing Patterns
- Test files: `src/pages/__tests__/` and `src/components/**/__tests__/`
- Wrapper: `QueryClientProvider` (retry: false) + `MemoryRouter` — create a fresh `QueryClient` per test
- Mock API modules: `vi.mock('@/api/<module>', () => ({ api: { method: vi.fn() } }))` at top of file
- Mock toaster: `vi.mock('@/components/ui/toaster', () => ({ toast: vi.fn() }))`
- Use `userEvent.setup()` for realistic browser-like interactions
- Prefer `screen.getByLabelText` / `screen.getByRole` over test IDs
- For pages that use `useParams`, wrap in `<Routes><Route path="/path/:id" element={...} /></Routes>` with `initialEntries`

### Observability
- Backend: Micrometer + `micrometer-tracing-bridge-otel` → OTLP → OTel Collector
- OTel Collector: metrics → Mimir, logs → Loki
- Custom metrics: `vote.created{mode}`, `vote.draw.performed{mode,round}`, `vote.participants.count`
- Grafana: pre-provisioned datasources (Mimir, Loki); add dashboard JSON files under `infra/monitoring/grafana/provisioning/dashboards/`

### Database Migrations
Flyway, files in `backend/src/main/resources/db/migration/`:
- V1: users
- V2: votes (enum types: vote_mode, vote_status)
- V3: vote_participants
- V4: draw_history
- V5: refresh_tokens
- V6: telegram_link_tokens
- V7: demo user seed (`admin@admin.com` / `admin`, idempotent insert)
- V8: vote_options table (title, position, unique per vote); adds `winner_option_id` / `winner_option_title` to draw_history; makes `winner_email` nullable

### Key Environment Variables
| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/fate` | JDBC URL |
| `JWT_ACCESS_SECRET` | (dev default set) | Must be ≥256-bit in production |
| `MAIL_HOST` | `localhost` | SMTP host (MailHog locally) |
| `FRONTEND_URL` | `http://localhost:3000` | For CORS and email links |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` | OTLP HTTP endpoint |
| `BOT_TOKEN` | — | Required; from @BotFather |
| `GRPC_SERVER_ADDR` | `localhost:9090` | Bot → backend gRPC address |
| `JWT_ACCESS_TTL_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_TTL_DAYS` | `30` | Refresh token lifetime |
| `LOG_LEVEL` | `info` | Bot log level |

## CI/CD

Services run directly on Ubuntu via systemd (no Docker). Nginx serves the frontend and proxies `/api/` to the backend.

### CI environment

| Component | Version      |
|-----------|--------------|
| Runner OS | ubuntu-24.04 |
| Java      | 21 (Temurin) |
| Go        | 1.24         |
| Node.js   | 22           |

### CI workflows (backend.yml / frontend.yml / bot.yml)
Each project has its own workflow file, triggered on PR and push to `main` via path filters. Each contains two jobs: test → build.
- `backend.yml`: `backend-test` → `backend-build` (artifact: `backend-jar`)
- `frontend.yml`: `frontend-test` → `frontend-build` (artifact: `frontend-dist`)
- `bot.yml`: `bot-test` → `build-bot` (artifact: `bot-binary`, built on `main` only)

### deploy.yml
Triggered when any of the three CI workflows completes, and via `workflow_dispatch`.

**`gate` job** — queries the GitHub API to confirm all three CI workflows passed for the same SHA. Skips deploy (`ready=false`) if not; the deploy fires when the last of the three finishes.

**`deploy` job** — downloads artifacts from the corresponding run-ids, copies to the server, restarts services.

**Flow:**
1. Downloads the three artifacts from CI runs
2. SCPs them to `/opt/hand-of-fate/{backend,frontend,bot}/` on the server
3. Writes `/opt/hand-of-fate/.env` from GitHub Secrets (overwrites on every deploy)
4. Restarts `fate-backend` and `fate-bot` systemd services, reloads Nginx
5. Health-checks `/actuator/health`; on failure prints last 50 lines of `journalctl`

### server-setup.yml
One-time `workflow_dispatch` — run on a fresh Ubuntu VPS before the first deploy. Steps:
1. Updates packages
2. Installs Java 21 (Temurin)
3. Installs and enables Nginx
4. Installs PostgreSQL 17, creates `fate` DB user and `fate` database
5. Creates `fate` system user and `/opt/hand-of-fate/{backend,frontend,bot}/` directories
6. Registers `fate-backend` and `fate-bot` systemd units with `EnvironmentFile=/opt/hand-of-fate/.env`
7. Writes Nginx site config: SPA routing (`/`), API proxy (`/api/`), Swagger UI (`/swagger-ui/`, `/v3/api-docs`), static asset caching

**Required GitHub Secrets:**
| Secret | Description |
|---|---|
| `DEPLOY_HOST` | Server IP or hostname |
| `DEPLOY_USER` | SSH user (`ubuntu`) |
| `DEPLOY_SSH_KEY` | Private SSH key |
| `DEPLOY_PORT` | SSH port (optional, default 22) |
| `DB_USERNAME` | PostgreSQL user (also used to create it) |
| `DB_PASSWORD` | PostgreSQL password |
| `DB_URL` | Full JDBC URL, e.g. `jdbc:postgresql://localhost:5432/fate` |
| `JWT_ACCESS_SECRET` | ≥256-bit random string |
| `BOT_TOKEN` | Telegram bot token from @BotFather |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials |
| `FRONTEND_URL` | Public URL for CORS and email links |
| `GRPC_SERVER_ADDR` | gRPC address for bot → backend (`localhost:9090`) |
