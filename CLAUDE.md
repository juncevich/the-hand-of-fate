# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**The Hand of Fate** — a voting/selection application with fair rotation support. Users create votes, invite participants by email or define a list of named options, and let a random draw choose a winner. The "Fair Rotation" mode ensures every participant/option wins once per round before anyone wins again.

## Monorepo Structure

```
backend/     Kotlin 2.4.0 + Spring Boot 4.1.0, PostgreSQL, gRPC server
frontend/    React 19 + TypeScript 6 + Vite 8 + Tailwind CSS 4 + shadcn/ui
bot/         Go 1.26.4 Telegram bot, gRPC client to backend
perf/        Gatling 3.13.5 + Kotlin load/smoke tests
simulation/  Go 1.25.0 user-behaviour simulator (functional end-to-end flows)
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

**Default demo user** (seeded by migration V7, **dev/test profiles only**): `admin@admin.com` / `admin`. The seed is gated behind the Flyway placeholder `seedDemoUser`, which defaults to `false` and is set to `true` only by the `dev`/`test` profiles — so it is never created in production.

### Backend (Kotlin + Gradle)
```bash
cd backend
./gradlew bootRun                # run locally (needs postgres on :5432)
./gradlew test                   # all tests
./gradlew test --tests "*.DrawServiceTest"  # single test class
./gradlew generateProto          # regenerate gRPC stubs from proto/
./gradlew bootJar                # build fat JAR
./gradlew detekt                 # static analysis (Detekt 1.23.8)
./gradlew spotlessCheck          # formatting check (Spotless 8.4.0 / ktlint)
./gradlew spotlessApply          # auto-fix formatting
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

### Simulation (Go)
```bash
cd simulation
go run ./cmd/simulate                          # all scenarios against localhost:8080
go run ./cmd/simulate -scenario simple         # single scenario: simple | fair | options | session
go run ./cmd/simulate -url https://host.com    # target non-local backend
go build -o fate-sim ./cmd/simulate            # build binary
```
- Scenarios: `session` (auth lifecycle), `simple` (options-based vote), `fair` (FAIR_ROTATION + round tracking), `options` (dynamic option add/remove)
- Each run registers fresh random users; no fixtures or seed data required
- Exits with code 1 and prints `FAIL <scenario>: <error>` on the first failure

### Performance Tests (Gatling)
```bash
cd perf
./gradlew gatlingRun                                              # all simulations
./gradlew gatlingRun --simulation simulations.SmokeSimulation    # smoke (1 user, all key endpoints)
./gradlew gatlingRun --simulation simulations.AuthSimulation     # register + login + refresh load
./gradlew gatlingRun --simulation simulations.VoteSimulation     # CRUD + draw load (assertions: p100 < 2s, >95% success)
./gradlew gatlingRun -DbaseUrl=https://your-server.com           # target non-local env
```
- Reports: `perf/build/reports/gatling/<simulation-name-timestamp>/index.html`
- JVM: requires Java 17+ to run Gradle; configured via `gradle.properties` (`org.gradle.java.home`)
- Simulations: `src/gatling/kotlin/simulations/`

**Direct dependencies** (from `bot/go.mod`):

| Package                            | Version  | Purpose       |
|------------------------------------|----------|---------------|
| `telegram-bot-api/telegram-bot-api/v5` | v5.5.1  | Telegram API  |
| `spf13/viper`                      | v1.21.0  | Config        |
| `go.uber.org/zap`                  | v1.28.0  | Logging       |
| `google.golang.org/grpc`           | v1.81.0  | gRPC client   |
| `google.golang.org/protobuf`       | v1.36.11 | Proto runtime |

### Telegram Bot Commands
| Command                                                 | Description                                                                                                                       |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `/start`, `/help`                                       | Welcome message and command list                                                                                                  |
| `/link <token>`                                         | Link Telegram to app account (token from Settings page)                                                                           |
| `/votes`                                                | List your votes with status and participant count                                                                                 |
| `/vote <id>`                                            | Vote details — title, mode, participants/options                                                                                  |
| `/newvote <title> \| <emails> \| <mode> [\| <options>]` | Create a new vote; `<emails>` — comma-separated, `<mode>` — `simple`/`fair`, `<options>` — optional comma-separated named options |
| `/draw <id>`                                            | Perform a draw for a vote (creator only)                                                                                          |
| `/result <id>`                                          | Show last draw result for a vote                                                                                                  |
| `/history <id>`                                         | Full draw history for a vote                                                                                                      |
| `/unlink`                                               | Unlink Telegram account                                                                                                           |

## Architecture

### Auth Flow
- Registration/login returns `accessToken` (JWT, 15 min) + `refreshToken` (UUID, 30 days)
- `refreshToken` is stored hashed in `refresh_tokens` table
- Frontend keeps `accessToken` in Zustand (memory only); `refreshToken` is sent via JSON body; all requests use `withCredentials: true`
- On app mount, `authApi.silentRefresh()` attempts to restore session via httpOnly cookie (`withCredentials: true`, empty body); failure is silently ignored (user stays logged out)
- On 401, Axios interceptor silently calls `/api/v1/auth/refresh`, queues concurrent requests, and retries once
- **Brute-force protection**: `RateLimitFilter` (in-memory fixed-window, per client IP) throttles `/api/v1/auth/{login,register,refresh}` — returns `429` with `Retry-After` past `app.rate-limit.capacity` per `app.rate-limit.window-seconds`. Single-instance only; move to a shared store (Redis) if scaled horizontally.
- **Fail-fast secrets**: `SecretsValidator` (`@Profile("!dev & !test")`) aborts startup if `JWT_ACCESS_SECRET` / `GRPC_SHARED_SECRET` are missing, too short, or left at a checked-in dev default — so production cannot boot with insecure secrets. Local runs use the `dev` profile to bypass this.

### Frontend State & Data Fetching
- **Zustand** manages auth state (`authStore`) and dark/light theme (`themeStore`; persisted to localStorage)
- **React Query** (`@tanstack/react-query`) handles all server state — queries, mutations, cache invalidation
- Custom Axios instance in `frontend/src/api/client.ts` handles token refresh with a retry queue so concurrent 401s only trigger one refresh call

**Custom hooks** (`frontend/src/hooks/`) encapsulate all React Query logic; pages import hooks rather than calling API directly:
- `useVoteList(page)` — paginated vote list query
- `useVoteDetail(id)` — vote query + all mutations (draw, reopen, addParticipant, removeParticipant, addOption, removeOption, deleteVote)
- `useTelegramLink()` — get/copy link token flow

**Vote detail sub-components** (`frontend/src/components/vote/`):
- `VoteHeader` — title, status/mode badges, draw and reopen buttons
- `VoteParticipants` — participant list with add/remove
- `VoteOptions` — options list with add/remove
- `VoteLastResult` — last draw result display
- `VoteHistory` — full draw history list

### Vote Modes
- **SIMPLE**: random draw from all participants or options, no history tracking
- **FAIR_ROTATION**: tracks `draw_history` per round; only participants/options who haven't won in `currentRound` are eligible. When all have won, `currentRound` increments and the cycle restarts

### Vote Draw Targets
A vote can draw from two mutually exclusive sources — if options exist they take precedence:
- **Participants** (default): draw winner from invited participants by email
- **Options**: named entries (e.g., tasks, topics) stored in `vote_options` table; draw picks one option. Options are created at vote creation time or added/removed individually via REST API (`POST /{id}/options`, `DELETE /{id}/options/{optionId}`)

`DrawWinner` is a sealed class with two subtypes: `Participant` and `Option`. `DrawHistory` stores the winner as either `winnerEmail` / `winnerDisplayName` (for participants) or `winnerOption` / `winnerOptionTitle` (for options — denormalized title for resilience to deletion).

### Backend Module Architecture (Hexagonal / Ports & Adapters)

The backend follows a hexagonal architecture enforced by **Spring Modulith 2.0**. Modules are: `auth`, `vote`, `shared`, `grpc`. Each module has an `internal/` subtree that is private to the module.

```
<module>/
  internal/
    domain/        — domain entities (plain Kotlin classes, no JPA annotations)
    port/          — repository/notification interfaces (ports)
    persistence/
      entity/      — JPA entities
      jpa/         — Spring Data JPA repositories
      adapter/     — JPA adapter implementations of port interfaces
      mapper/      — domain ↔ entity mappers
    web/           — REST controllers and DTOs
    notification/  — notification adapters (email via NotificationPort)
```

`ModularityTest` (`backend/src/test/kotlin/com/juncevich/fate/ModularityTest.kt`) verifies module boundaries are respected and generates PlantUML diagrams.

### Threading Model
- **Virtual threads (Project Loom)** enabled via `spring.threads.virtual.enabled: true`
- Tomcat uses `VirtualThreadExecutor` for all HTTP request threads
- `@Async` tasks (`NotificationAdapter`) run on virtual threads — `Thread.sleep()` in retry logic parks the virtual thread correctly

### Key Backend Services
- **DrawService**: core draw logic with SIMPLE / FAIR_ROTATION branching for both participants and options; picks draw target automatically (options if any exist, otherwise participants)
- **VoteService**: CRUD for votes including creating `VoteOption` entities from request; `addOption()` / `removeOption()` for post-creation management; votes use optimistic locking (`@Version`) to prevent concurrent draw conflicts
- **NotificationService**: async dispatcher — triggers email after draws/invitations, swallows errors so draw success is never blocked by notification failure
- **EmailService**: sends styled HTML emails (dark theme) for vote invitations and draw results
- **FateGrpcService**: gRPC server implementation; uses `runCatching` + `StatusRuntimeException` for error mapping; maps `VoteOptionInfo` proto messages for options

### gRPC (Backend ↔ Bot)
- Proto source: `proto/fate/v1/fate.proto` (package `fate.v1`)
- Buf manages proto (`buf.yaml` + `buf.gen.yaml` at repo root)
- Backend generates Java/Kotlin stubs via `com.google.protobuf` Gradle plugin → `backend/build/generated/source/proto/main/`
- Bot generates Go stubs via Buf → `bot/gen/fate/v1/`
- `FateGrpcService.kt` implements the service; `bot/internal/grpcclient/` wraps the Go stub
- **Transport security:** the channel is plaintext (no TLS) authenticated only by `SharedSecretAuthInterceptor` (constant-time compare of `x-grpc-shared-secret`). The server binds to `127.0.0.1` by default (`GRPC_BIND_ADDRESS`) so it is not reachable off-host; only set `0.0.0.0` on a trusted network. For a topology where the bot and backend run on different hosts, add mTLS.
- `getMyVotes` aggregates **all** of the caller's votes by paging server-side (up to `GRPC_MAX_PAGES` × 50), rather than returning only the first page.
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
- **Backend**: MockK for unit tests. Tests live in `backend/src/test/kotlin/`
- **Frontend**: Vitest + `@testing-library/react` + jest-dom

#### Backend Testing Patterns
- Test files mirror the module structure: `auth/`, `vote/internal/`, `grpc/`, `shared/`
- `ModularityTest` verifies Spring Modulith boundaries — run it after any package restructuring
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
- V7: demo user seed (`admin@admin.com` / `admin`, idempotent insert) — **gated by the `seedDemoUser` Flyway placeholder (dev/test only, default off)**
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
| `GRPC_SHARED_SECRET` | (dev default set) | Shared secret authenticating bot → backend gRPC calls; must match on both sides, change before any real deployment |
| `GRPC_BIND_ADDRESS` | `127.0.0.1` | Interface the backend gRPC server binds to. Loopback by default (plaintext + shared-secret only); set `0.0.0.0` on a trusted internal network (Docker compose already does) |
| `JWT_ACCESS_TTL_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_TTL_DAYS` | `30` | Refresh token lifetime |
| `LOG_LEVEL` | `info` | Bot log level |
| `SPRING_PROFILES_ACTIVE` | (none) | Set to `dev` for local runs (seeds demo user, disables Secure cookies, skips the production secret guard). Unset in production so `SecretsValidator` enforces non-default secrets |
| `REFRESH_COOKIE_SECURE` | `true` | Secure flag on the refresh cookie; forced to `false` by the `dev` profile for local HTTP |
| `SPRINGDOC_ENABLED` | `false` | Swagger UI + `/v3/api-docs`. Off in prod; the `dev` profile turns it on for local use |
| `app.rate-limit.capacity` / `app.rate-limit.window-seconds` | `10` / `60` | Per-IP request budget on `/api/v1/auth/{login,register,refresh}` |

## CI/CD

Services run directly on Ubuntu via systemd (no Docker). Nginx serves the frontend and proxies `/api/` to the backend.

### CI environment

| Component | Version      |
|-----------|--------------|
| Runner OS | ubuntu-24.04 |
| Java      | 21 (Temurin) |
| Go        | 1.25         |
| Node.js   | 22           |

### CI workflows (backend.yml / frontend.yml / bot.yml)
Each project has its own workflow file, triggered on PR and push to `main` via path filters. Each contains two jobs: test → build.
- `backend.yml`: `backend-test` → `backend-build` (artifact: `backend-jar`)
- `frontend.yml`: `frontend-test` → `frontend-build` (artifact: `frontend-dist`)
- `bot.yml`: `bot-test` → `build-bot` (artifact: `bot-binary`, built on `main` only)

### Deploy workflows (deploy-backend.yml / deploy-frontend.yml / deploy-bot.yml)
Each component has its own deploy workflow triggered by its CI workflow completing and via `workflow_dispatch`.

**`gate` job** — checks if the triggering CI workflow succeeded; on `workflow_dispatch` finds the latest successful CI run. Skips deploy (`ready=false`) if not.

**`deploy` job** — downloads the artifact, copies to the server, restarts the relevant service.

| Workflow | Triggered by | Artifact | Service action |
|---|---|---|---|
| `deploy-backend.yml` | Backend CI | `backend-jar` | restart `fate-backend`, health-check |
| `deploy-frontend.yml` | Frontend CI | `frontend-dist` | reload `nginx` |
| `deploy-bot.yml` | Bot CI | `bot-binary` | restart `fate-bot` |

Backend and bot deploys also write `/opt/hand-of-fate/.env` from GitHub Secrets.

### server-setup.yml
One-time `workflow_dispatch` — run on a fresh Ubuntu VPS before the first deploy. Optional inputs `domain` + `email` enable TLS. Steps:
1. Updates packages
2. Installs Java 21 (Temurin)
3. Installs and enables Nginx
4. Installs PostgreSQL 17, creates `fate` DB user and `fate` database
5. Creates `fate` system user and `/opt/hand-of-fate/{backend,frontend,bot}/` directories
6. Registers `fate-backend` and `fate-bot` systemd units with `EnvironmentFile=/opt/hand-of-fate/.env`
7. Writes Nginx site config: SPA routing (`/`), API proxy (`/api/`), per-IP `limit_req` on `/api/v1/auth/`, security headers (CSP, HSTS, X-Frame-Options, nosniff, Referrer-Policy), static asset caching. **Swagger/OpenAPI is not proxied in prod** (also disabled server-side unless `SPRINGDOC_ENABLED=true`).
8. **TLS**: if `domain` + `email` inputs are provided, installs certbot and runs `certbot --nginx --redirect` (Let's Encrypt cert + 80→443 redirect). Without them it serves HTTP only and warns that the `Secure` refresh cookie will be dropped — provide a domain or terminate TLS upstream for a real deployment.

> **Docker note:** the backend image builds with the **repository root** as context (`docker-compose` sets `context: .` + `dockerfile: backend/Dockerfile`) so the shared `proto/` tree is reachable.

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
| `GRPC_SHARED_SECRET` | Shared secret authenticating bot → backend gRPC calls; same value on both deploy workflows |
