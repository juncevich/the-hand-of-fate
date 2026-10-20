# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**The Hand of Fate** — a voting/selection application with fair rotation support. Users create votes, invite participants by email or define a list of named options, and let a random draw choose a winner. The "Fair Rotation" mode ensures every participant/option wins once per round before anyone wins again.

## Monorepo Structure

```
backend/     Kotlin 2.4.20 + Spring Boot 4.1.1 on Java 26, PostgreSQL, gRPC server
frontend/    React 19 + TypeScript 7 + Vite 8 + Tailwind CSS 4 + shadcn/ui
bot/         Go 1.27.1 Telegram bot, gRPC client to backend
perf/        Gatling 3.13.5 + Kotlin load/smoke tests
simulation/  Go 1.27.1 user-behaviour simulator (functional end-to-end flows)
e2e/         Playwright browser tests against a running stack (minikube gateway by default)
proto/       Shared protobuf definitions (proto/fate/v1/fate.proto)
api/         REST contract api/openapi.yaml (generated from the backend controllers, committed)
infra/
  nginx/     Nginx reverse proxy configs
  monitoring/ OTel Collector, Loki, Mimir, Grafana provisioning
  k8s/       Kubernetes manifests: base/ (apps) + overlays/local (minikube; no prod environment planned)
.github/
  workflows/ backend.yml + frontend.yml + bot.yml + perf.yml + simulation.yml + api.yml + k8s.yml + e2e.yml + deploy-*.yml + server-setup.yml
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
make infra                    # start only postgres + mailpit + rustfs + keycloak
make infra-down               # stop infrastructure
```
- `docker-compose.infra.yml` — lightweight compose file with only postgres, mailpit, rustfs and Keycloak (used by `dev-start.sh`)
- `dev-start.sh` handles stale process cleanup, colored log output per service, conditional bot startup (skipped if `BOT_TOKEN` unset)

- Frontend:  http://localhost:3000
- Backend:   http://localhost:8080
- Swagger:   http://localhost:8080/swagger-ui.html
- Mailpit:   http://localhost:8025
- RustFS:    http://localhost:9001 (S3 console, `rustfsadmin`/`rustfsadmin`; S3 API on :9000)
- Grafana:   http://localhost:3001  (admin/admin; dashboard "Hand of Fate — Overview", Mimir/Loki/Tempo)
- Keycloak:  admin console http://localhost:8180/auth/admin (admin/admin); realm `fate` is served on the SPA origin (`/auth/` — Vite proxy, or the gateway in the full compose at http://localhost/auth). Being introduced as the authorization server — see `docs/adr/0001-keycloak-authorization-server.md` and the checklist `docs/plans/keycloak-auth.md`

**Option 3 — everything in local Kubernetes (minikube):**
```bash
./k8s-start.sh                       # create/resume minikube profile `hand-of-fate`, build images, deploy, wait
./k8s-start.sh --skip-build          # re-apply manifests only
./k8s-start.sh --only backend        # rebuild + restart selected apps (backend,frontend,bot)
./k8s-stop.sh                        # stop the cluster, data kept
./k8s-stop.sh --delete               # delete the cluster with all data
# or via make: k8s-up / k8s-down / k8s-delete / k8s-status; `make k8s-check` = static checks (as in CI)

make k8s-dev                         # hot-reload loop: k8s-start.sh --cluster-only + `tilt up` (UI http://localhost:10350)
make k8s-dev-down                    # `tilt down` — removes the workloads, keeps the Postgres/RustFS volumes
```
- Manifests: `infra/k8s/base/` (backend, frontend, bot, Keycloak + the `keycloak-config` Job, nginx `gateway` that reuses `infra/nginx/nginx.conf`) + `infra/k8s/overlays/local/` (Postgres StatefulSet, Keycloak's database and dev realm, RustFS, Mailpit, dev config/secrets, NodePort services). Build with `kubectl kustomize --load-restrictor LoadRestrictionsNone infra/k8s/overlays/local` — the base reads the nginx config from outside its directory
- Images are built by the host Docker (`buildx build --load`, cache shared with docker-compose) as `fate-*:dev` and copied into the node with `minikube image load` — never pushed; the tag doesn't change, so the script `rollout restart`s rebuilt apps
- `overlays/local/write-secrets.sh` (called by `k8s-start.sh` and the Tiltfile) writes `overlays/local/.secrets.env` (gitignored) from `.env`: `GRPC_SHARED_SECRET`, `BOT_TOKEN`; without a token the bot is scaled to 0 (Tilt doesn't deploy it). It also writes `.oauth.env` (`OAUTH_<GOOGLE|GITHUB|YANDEX>_CLIENT_ID/SECRET`, social login)
- **Keycloak** (`infra/keycloak/`): realm `fate` is code — `realm/fate.yaml` (+ `realm-dev/fate-dev.yaml`, the demo user, dev/e2e only), applied idempotently by keycloak-config-cli (`apply-config.sh`; compose service / k8s Job `keycloak-config`, which `k8s-start.sh` deletes and recreates on every run). A social login provider is enabled only when its `OAUTH_*_CLIENT_ID` is set. Its database `keycloak` lives in the same Postgres (`create-database.sh`, idempotent). The gateway proxies `/auth/` with its own headers (Keycloak sends CSP/X-Frame-Options itself) and a per-IP limit on POSTs; metrics (`keycloak_user_events_total`) are scraped by the collector, traces go to Tempo
- The cluster runs the **docker** container runtime (`--container-runtime=docker`) so Tilt builds straight into the node's daemon; `k8s-start.sh` warns about an older containerd cluster — recreate it with `./k8s-stop.sh --delete`
- **Tilt** (`Tiltfile`, `infra/k8s/overlays/tilt/`) deploys the same manifests with dev images:
  - frontend: Vite dev server (`frontend/Dockerfile.dev`), `frontend/` synced live, HMR over the gateway (nginx proxies the WebSocket upgrade). `package*.json` / `vite.config.ts` changes rebuild the image. The tilt overlay drops the gateway CSP (React Refresh injects an inline script) via `.tilt-build/nginx.conf`, which the Tiltfile generates — build that overlay only through Tilt
  - backend: `backend-jar` local resource (`./gradlew bootJar`) → `.tilt-build/backend/app.jar` → thin `backend/Dockerfile.dev` → pod restart (~30 s from save to ready). Swagger via Tilt port-forward: http://localhost:18080/swagger-ui.html
  - bot: host `go build` (GOOS=linux) → `.tilt-build/bot/fate-bot` → `bot/Dockerfile.dev`; only when `BOT_TOKEN` is set
  - `rustfs-data`, `loki-data`, `mimir-data`, `tempo-data`, `grafana-data` PVCs carry `tilt.dev/down-policy: keep`; Postgres' StatefulSet volume is never deleted by Tilt
- **Monitoring** — `infra/k8s/components/observability/` (a kustomize Component included by `overlays/local`; drop it from `components:` to run without): OTel Collector DaemonSet, Loki, Mimir, Tempo, Grafana (with the provisioned dashboards), all on the `infra/monitoring/*` configs. The collector additionally gets `otel-collector-k8s.yaml` (second `--config`, merged): a `filelog` receiver tails `/var/log/pods/hand-of-fate_*` (with the docker runtime those are symlinks into `/var/lib/docker/containers`), so every pod's stdout lands in Loki with `service_name` = container name (`backend`, `nginx` for the gateway, …) and `k8s_*` labels. `start_at: beginning` + `file_storage` offsets on a hostPath: startup logs are kept, collector restarts don't duplicate. The backend's OTLP metrics go to Mimir via `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318`
- Host access goes through NodePorts published by `minikube start --ports` (fixed at cluster creation — `--delete` and recreate after changing them). The 30xxx ports don't clash with docker-compose, so both setups can run at once:
  - App (gateway): http://localhost:30080 · Keycloak admin: http://localhost:30080/auth/admin/ · Mailpit: http://localhost:30025 · RustFS console: http://localhost:30901/rustfs/console/ · Postgres: `localhost:30432` · Grafana: http://localhost:30300 (admin/admin)
  - `k8s-start.sh` warns when the node doesn't publish a port from `HOST_PORTS` (cluster created before the port was added)
  - Swagger: `kubectl --context hand-of-fate -n hand-of-fate port-forward svc/backend 18080:8080`
- Everything uses kubectl context `hand-of-fate` explicitly; the scripts never touch other contexts. Cluster size: `MINIKUBE_CPUS` (4) / `MINIKUBE_MEMORY` (8g — Keycloak needs ~1 GiB), applied on creation

**Default demo user** (seeded by migration V7, **dev/test profiles only**): `admin@admin.com` / `admin`. The seed is gated behind the Flyway placeholder `seedDemoUser`, which defaults to `false` and is set to `true` only by the `dev`/`test` profiles — so it is never created in production. In Keycloak (realm `fate`, `infra/keycloak/realm-dev/`) the same address signs in with `admin123` — the realm's password policy needs 8+ characters.

### Backend (Kotlin + Gradle)
```bash
cd backend
./gradlew bootRun                # run locally (needs postgres on :5432)
./gradlew test                   # all tests
./gradlew test --tests "*.DrawServiceTest"  # single test class
./gradlew jacocoTestReport       # unit + integration coverage → build/reports/jacoco/test/html/index.html (also run by `check`)
./gradlew jacocoTestCoverageVerification  # fails below 95% lines / 85% branches (also run by `check`)
./gradlew generateProto          # regenerate gRPC stubs from proto/
./gradlew bootJar                # build fat JAR
./gradlew detekt                 # static analysis (detekt 2.0.0-alpha.6, full analysis of main/test/integrationTest)
./gradlew detektBaselineMain detektBaselineTest detektBaselineIntegrationTest  # rewrite detekt-baseline-<sourceSet>.xml
./gradlew spotlessCheck          # formatting check (Spotless 8.4.0 / ktlint)
./gradlew spotlessApply          # auto-fix formatting
```

### Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev          # dev server on :3000 (proxies /api → :8080)
npm run dev:mock     # dev server with mock API (no backend needed; demo@example.com / password, mailed links go to the console)
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

### OpenAPI (REST contract)
```bash
# From repo root:
make openapi            # openapi-spec + openapi-clients
make openapi-spec       # backend: ./gradlew openApiUpdate → api/openapi.yaml (boots the app on Testcontainers)
make openapi-clients    # frontend: npm run gen:api → src/api/generated/schema.ts; simulation: go generate ./...
```
- Code-first: the controllers are the source of truth; `api/openapi.yaml` is their committed snapshot (OpenAPI 3.0 — oapi-codegen and oasdiff fully support only 3.0)
- A deliberate breaking REST change is listed with its reason in `api/accepted-breaking-changes.txt` (passed to `oasdiff --err-ignore` by `api.yml`), together with updating every client in the repo; clear the file once on main
- After changing a controller or a REST DTO: `make openapi` and commit the spec together with both generated clients. `OpenApiSpecIntegrationTest` (backend `check`), `npm run check:api` (frontend CI) and the simulation CI fail on a stale file
- Spec shaping lives in `shared/internal/config/OpenApiConfig.kt`: bearer auth is the default security (public endpoints use an empty `@SecurityRequirements`), every operation gets `401` (if authenticated) and a `default` `ProblemDetail` response, and `KotlinRequiredPropertiesConverter` marks constructor params without a default value as `required` (nullable ones stay `nullable`, since Jackson writes nulls). Shared names (`bearerAuth`, `refreshCookie`, `ProblemDetail`) are in `shared/OpenApiDocs.kt`
- Give every endpoint `@Operation(operationId = ...)` — generated clients use it — and a status via `@ResponseStatus` (or `@ApiResponse` when the method must return `ResponseEntity` for headers). Enums shared by several DTOs get `@Schema(enumAsRef = true)`
- Frontend: `src/types/vote.ts` and `src/api/*.ts` alias `components['schemas'][...]` from the generated file; don't hand-write REST types. `openapi-typescript` declares `typescript@^5` as a peer, so an npm `overrides` entry points it at the TS 6 alias
- Simulation: `internal/client/models.gen.go` via `oapi-codegen` (a `tool` in `simulation/go.mod`, config `internal/client/oapi-codegen.yaml`: models only, UUID/email as plain strings, int32 as `int`, nullable fields as pointers)

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
go run ./cmd/simulate -mailpit http://localhost:30025   # Mailpit of the minikube stack (default :8025)
go build -o fate-sim ./cmd/simulate            # build binary
```
- Scenarios: `session` (auth lifecycle), `simple` (options-based vote), `fair` (FAIR_ROTATION + round tracking), `options` (dynamic option add/remove)
- Each run registers fresh random users and confirms them by reading the verification email from Mailpit's API (`internal/mailbox`); no fixtures or seed data required
- Exits with code 1 and prints `FAIL <scenario>: <error>` on the first failure

### Browser tests (Playwright)
```bash
make e2e-web                                   # npm ci + chromium + playwright test, against the minikube gateway
cd e2e && npx playwright test tests/votes.spec.ts   # one spec
E2E_BASE_URL=http://localhost:3000 E2E_MAILPIT_URL=http://localhost:8025 make e2e-web   # against dev-start.sh
```
- `dev-start.sh` starts the backend with the per-IP auth limit raised to 1000/min (`DEV_AUTH_RATE_LIMIT`, set it to 10 to try the production limit), so a full run works there too
- Nothing is started by the tests: deploy first (`K8S_OVERLAY=e2e ./k8s-start.sh` — its overlay raises the backend's and the gateway's per-IP auth rate limits; `overlays/e2e/prepare.sh` writes the gateway config to `.e2e-build/nginx.conf`)
- Specs in `e2e/tests/`: `auth` (sign-up, password reset), `account` (unsubscribe, deletion, password change, email preferences, avatar), `votes` (FAIR_ROTATION rounds, options vote, history paging); `e2e/support/` registers fresh users and follows the mailed links via Mailpit; `apiAs()` (`support/api.ts`) signs in over REST in the separate `request` context for setup that would be slow through the UI (e.g. 21 draws). One worker — the tests share the stack's rate limits and mailbox
- `npm run typecheck` (TS 6 alias, like the frontend's ESLint side); failure traces/screenshots land in `e2e/test-results/`

### Performance Tests (Gatling)
```bash
cd perf
./gradlew gatlingRun                                              # all simulations
./gradlew gatlingRun --simulation simulations.SmokeSimulation    # smoke (1 user, all key endpoints)
./gradlew gatlingRun --simulation simulations.AuthSimulation     # register + login + refresh load
./gradlew gatlingRun --simulation simulations.VoteSimulation     # CRUD + draw load (assertions: p100 < 2s, >95% success)
./gradlew gatlingRun -DbaseUrl=https://your-server.com           # target non-local env
./gradlew detekt                                                  # static analysis
./gradlew spotlessCheck                                           # formatting check
./gradlew spotlessApply                                           # auto-fix formatting
```
- Reports: `perf/build/reports/gatling/<simulation-name-timestamp>/index.html`
- JVM: Gradle provisions Temurin 26 for itself (`gradle/gradle-daemon-jvm.properties`) and for the Java 26 toolchain; `gatlingRun` runs simulations in the Gradle daemon JVM
- Simulations: `src/gatling/kotlin/simulations/`
- `perf/detekt.yml` mirrors the backend's ruleset but disables `WildcardImport` (Gatling's Java DSL is conventionally imported via `CoreDsl.*`/`HttpDsl.*`) and relaxes `FunctionNaming` (PascalCase factory functions like `LongFeeder(...)`)

**Direct dependencies** (from `bot/go.mod`):

| Package                            | Version  | Purpose       |
|------------------------------------|----------|---------------|
| `telegram-bot-api/telegram-bot-api/v5` | v5.5.1  | Telegram API  |
| `spf13/viper`                      | v1.21.0  | Config        |
| `go.uber.org/zap`                  | v1.28.0  | Logging       |
| `google.golang.org/grpc`           | v1.84.0  | gRPC client   |
| `google.golang.org/genproto/googleapis/rpc` | (pseudo-version, via grpc) | `errdetails.ErrorInfo` |
| `google.golang.org/protobuf`       | v1.36.12 | Proto runtime |
| `go.opentelemetry.io/otel` (+ `sdk`, `sdk/metric`, `otlptracegrpc`, `otlpmetricgrpc`) | v1.46.0 | Traces + metrics over OTLP/gRPC |
| `go.opentelemetry.io/contrib/.../grpc/otelgrpc` | v0.71.0 | gRPC client spans/metrics, `traceparent` propagation |

### Telegram Bot Commands
| Command                                                 | Description                                                                                                                       |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `/start`, `/help`                                       | Welcome message and command list                                                                                                  |
| `/link <token>`                                         | Link Telegram to app account (token from Settings page)                                                                           |
| `/votes [page]`                                         | List your votes with status and participant count (10 per page; pages start at 1)                                                 |
| `/vote <id>`                                            | Vote details — title, mode, draw target, participants (observers of an options vote) and options                                  |
| `/newvote <title> \| <emails> \| <mode> [\| <options>]` | Create a new vote; `<emails>` — comma-separated, `<mode>` — `simple`/`fair`, `<options>` — optional comma-separated named options |
| `/draw <id>`                                            | Perform a draw for a vote (creator only)                                                                                          |
| `/result <id>`                                          | Show last draw result for a vote                                                                                                  |
| `/history <id> [page]`                                         | Paginated draw history (20 entries; pages start at 1)                                                                                                      |
| `/unlink`                                               | Unlink Telegram account                                                                                                           |

The bot answers only in private chats and identifies the user by `message.From.ID` (in a group the chat id is shared by every member). A command sent in a group gets a "private messages only" reply; `/link` there also triggers a best-effort delete of the message and a warning to generate a new token. Replies use Telegram HTML parse mode: user-supplied values go through `esc()`; messages over 4096 UTF-16 units are split between lines, and markup Telegram rejects (400) is resent as plain text.

## Architecture

### Auth Flow
- **Registration requires email verification**: `POST /auth/register` answers `202 {email}` without a session and mails a link (`/verify-email?token=…`, valid 24 h). Login with the right password on an unconfirmed address is `403` with problem `code: EMAIL_NOT_VERIFIED` (only after the password matched — no account enumeration). `POST /auth/verify-email {token}` confirms and signs in. `resend-verification` / `forgot-password` always answer `202`, mail at most once a minute per account (`app.auth.email-token-cooldown`) and are IP-rate-limited. `reset-password {token,newPassword}` (link valid 30 min) ends every session and also confirms the address; `change-password` (authenticated, current password required — a wrong one is `400 WRONG_PASSWORD` and counts towards `LoginThrottle`) ends every other session and returns a fresh one. One-time tokens (`email_tokens`, V23) are 32 random bytes stored as SHA-256, one per user and purpose, consumed on first use. Accounts that existed before V23 (incl. the demo user) count as verified. Logic: `auth/internal/service/AccountService`, endpoints in `AuthController` (those that set the cookie) and `AccountController`
- **Migrating to Keycloak** (docs/adr/0001, checklist docs/plans/keycloak-auth.md): the resource server already accepts Keycloak's RS256 tokens next to its own — `IssuerRoutingJwtDecoder` / `IssuerRoutingAuthenticationConverter` (`auth/internal/token/KeycloakTokens.kt`) route by `iss` (the app's own tokens carry none). A Keycloak token must have `aud` ∋ `fate-api` and `email_verified=true`; on first use `KeycloakUserProvisioner` creates the `users` row with id = `sub` (or takes over the pre-Keycloak account with that verified email), `auth_time` becomes `AuthenticatedUser.authenticatedAt` (delete-account without a password needs a sign-in ≤ 5 min ago, else `403 REAUTHENTICATION_REQUIRED`). `KeycloakAdminClient` (service account `fate-backend`) ends sessions on `logout-all` and, via `IdentityDeletionWorker`, deletes the Keycloak user of a deleted account. Integration tests mint Keycloak tokens with `FakeKeycloak` (`keycloakToken(...)`); `KeycloakSmokeIntegrationTest` runs a real Keycloak configured from `infra/keycloak/`
- Login/verification returns `accessToken` (HS256 JWT, 15 min — issued with `NimbusJwtEncoder`, validated by Spring Security's OAuth2 resource server, see `auth/internal/token/JwtConfig.kt`) + `refreshToken` (UUID, 30 days)
- `refreshToken` is stored hashed in `refresh_tokens` table. Each refresh rotates it: the old row gets `revoked_at` and its successor keeps the session's `family_id` (a login starts a new family). A rotated token replayed within `jwt.refresh-reuse-grace-seconds` (30) is a concurrent refresh and is served; replayed later it is treated as stolen and the whole family is deleted (log warning, both holders must log in again). Logout deletes the token's family, `logout-all` every family of the user
- `ExpiredTokenPurger` deletes expired refresh and Telegram link tokens hourly (`app.auth.token-purge-delay-ms`)
- Frontend keeps `accessToken` in Zustand (memory only); `refreshToken` stays in an httpOnly cookie; all requests use `withCredentials: true`
- On app mount, `authApi.silentRefresh()` attempts to restore session via httpOnly cookie (`withCredentials: true`, empty body); failure is silently ignored (user stays logged out)
- On 401, Axios interceptor silently calls `/api/v1/auth/refresh`, shares a refresh Promise with initial session restoration and retries each protected request once
- **Brute-force protection**: `RateLimitFilter` (in-memory fixed-window, per client IP) throttles `/api/v1/auth/{login,register,refresh}` — returns `429` with `Retry-After` past `app.rate-limit.capacity` per `app.rate-limit.window-seconds`. The client IP is `request.remoteAddr` as resolved by Tomcat's RemoteIpValve (`server.forward-headers-strategy: native`): `X-Forwarded-For` counts only when the direct peer is a trusted internal proxy (loopback/private ranges; override `server.tomcat.remoteip.internal-proxies`), so a client reaching the backend directly can't spoof it. `LoginThrottle` additionally limits failed logins per account (normalized email, unknown addresses included): past `app.rate-limit.login-failures-per-account` per `login-failure-window-seconds` login answers `429` + `Retry-After` without checking the password; a success resets the count. Both are single-instance only; move to a shared store (Redis) if scaled horizontally.
- **Fail-fast secrets**: `SecretsValidator` (`@Profile("!dev & !test")`) aborts startup if `JWT_ACCESS_SECRET` / `GRPC_SHARED_SECRET` / `UNSUBSCRIBE_SECRET` are missing, too short, or left at a checked-in dev default — so production cannot boot with insecure secrets. Local runs use the `dev` profile to bypass this.

### Frontend State & Data Fetching
- **Zustand** manages auth state (`authStore`) and dark/light theme (`themeStore`; persisted to localStorage)
- **React Query** (`@tanstack/react-query`) handles all server state — queries, mutations, cache invalidation
- Custom Axios instance in `frontend/src/api/client.ts` shares one pending refresh Promise across initial restoration and concurrent 401s; auth endpoints are excluded. Across tabs the refresh call runs under the Web Lock `fate-auth-refresh`, so a second tab refreshes only after the first, with the already rotated cookie
- TypeScript 7 runs side-by-side with the 6.0 API: `tsc` (type-check in `npm run build`) is TS 7 from the `@typescript/native` alias, while the `typescript` package is aliased to `@typescript/typescript6` because typescript-eslint needs the classic JS API that TS 7 no longer ships
- ESLint enforces `@typescript-eslint/no-floating-promises` on `src/**` (type-checked, see `eslint.config.js`) — mark deliberately-unawaited calls (`invalidateQueries()`, `navigate()`, `clipboard.writeText()`, etc.) with `void` rather than leaving them bare

**Custom hooks** (`frontend/src/hooks/`) encapsulate all React Query logic; pages import hooks rather than calling API directly:
- `useVoteList(page)` — paginated vote list query
- `useVoteDetail(id)` — vote query + all mutations (draw, reopen, addParticipant, removeParticipant, addOption, removeOption, deleteVote)
- `useTelegramLink()` — get/copy link token flow
- `useAvatar()` — upload (client-side 2 MB check) / delete avatar, updates `authStore.avatarUrl`

**Vote detail sub-components** (`frontend/src/components/vote/`):
- `VoteHeader` — title, status/mode badges, draw and reopen buttons
- `VoteParticipants` — participant list with add/remove
- `VoteOptions` — options list with add/remove
- `VoteLastResult` — last draw result display
- `VoteHistory` — paginated history (20 entries per page)

### Vote Modes
- **SIMPLE**: random draw from all participants or options; history is recorded but does not affect eligibility
- **FAIR_ROTATION**: tracks `draw_history` per round; only participants/options who haven't won in `currentRound` are eligible. When all have won, `currentRound` increments and the cycle restarts

### Vote Limits
`VoteLimits`: description ≤ 2000 chars, ≤ 100 participants (creator included), ≤ 100 options. Validated by the REST DTO and enforced again in `VoteService` (gRPC, add-participant/add-option) → 400 / gRPC failure response.

### Vote Draw Targets
Each vote has a `drawTarget` (`votes.draw_target`, V20), chosen at creation and never changed:
- **PARTICIPANTS**: the winner is one of the invited participants. Such a vote cannot have options — creating it with options is 400, `addOption` is 409
- **OPTIONS**: the winner is one of the named entries in `vote_options` (tasks, topics…). Participants are *observers*: they see the vote and get its emails but are never drawn (UI and bot label them so). A draw without options is 409 ("add at least one option first")
- When a client omits it (REST `drawTarget`, gRPC `DRAW_TARGET_UNSPECIFIED`, the bot's `/newvote`), it is inferred: OPTIONS if options are given, else PARTICIPANTS — so older clients keep working
- Options are created with the vote or managed via REST (`POST /{id}/options`, `DELETE /{id}/options/{optionId}`, `PUT /{id}/options/order` with every option id in the new order). Positions are `0..n-1`, unique per vote (`DEFERRABLE INITIALLY DEFERRED`, V21, so a reorder can swap them); a new option goes after the highest position

`DrawWinner` is a sealed class with two subtypes: `Participant` and `Option`. `DrawHistory` stores the winner as either `winnerEmail` / `winnerDisplayName` (for participants) or `winnerOption` / `winnerOptionTitle` (for options — denormalized title for resilience to deletion).

### Backend Module Architecture (Hexagonal / Ports & Adapters)

The backend follows a hexagonal architecture enforced by **Spring Modulith 2.0**. Modules are: `auth`, `vote`, `shared`, `grpc`, `storage`, `notification`. Each module has an `internal/` subtree that is private to the module.

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
    notification/  — durable event recording and SMTP delivery from a PostgreSQL outbox
```

`ModularityTest` (`backend/src/test/kotlin/com/juncevich/fate/ModularityTest.kt`) verifies module boundaries are respected and generates PlantUML diagrams.

### Object Storage & Avatars
- **Unsubscribe**: every invitation / draw-result email links to `/unsubscribe?email=…&sig=…` (HMAC-SHA256 of the address with `UNSUBSCRIBE_SECRET`, `EmailPreferences.sign`) and carries `List-Unsubscribe` + `List-Unsubscribe-Post` (RFC 8058) pointing at `POST /api/v1/notifications/unsubscribe/one-click`. The SPA page unsubscribes only on a click (link scanners prefetch URLs). Works for any address, account or not; the address goes to `email_suppressions` (V24) and `NotificationWorker` skips its notification jobs at delivery time (so queued ones too; metric `notification.suppressed{reason=unsubscribed}`, the budget uses `reason=budget`). Account emails (verification, reset) are always delivered. A signed-in user toggles it at `GET/PUT /api/v1/users/me/email-preferences` (Settings → «Письма»), which also undoes an unsubscribe
- **Account deletion**: `POST /api/v1/auth/delete-account {password}` (wrong password → `400 WRONG_PASSWORD`, counts towards `LoginThrottle`). In one transaction: `AccountDeleted` event → the vote module (`AccountDeletionListener`) removes the address from every vote's participants and anonymizes its past wins (`winner_email` NULL, name "Deleted user"; `DrawHistory.ParticipantWinner.email` is nullable for this), queued emails to the address are dropped (`NotificationOutbox.cancelFor`), the user row goes and its own votes, tokens and budget ledger cascade. The avatar object is deleted after commit (best effort). Settings shows the number of own votes first (`GET /api/v1/votes/stats`)
- `notification` module: all outgoing email — the outbox (`NotificationOutbox.record(payload)`, call it inside the transaction that causes the email), the worker, SMTP templates (`EmailService`; user-supplied text is HTML-escaped) and the per-user `EmailBudget`. Its public API is `NotificationOutbox`, `EmailBudget` and the versioned payloads (`NotificationPayloads.kt`); it depends only on `shared` (`ModularityTest`). `vote` records invitations/draw results from its domain events (`vote/internal/notification/NotificationRecorder`), `auth` records verification and reset emails directly
- `storage` module: public port `ObjectStorage` (put/get/delete) with an S3 adapter (AWS SDK v2, `url-connection-client`). It depends only on `shared` (`ModularityTest`). Any S3-compatible store works; locally it is **RustFS** (MinIO no longer ships community Docker images)
- `app.storage.enabled` (`S3_ENABLED`) defaults to `false` so a deployment without a bucket still boots: `DisabledObjectStorage` is wired instead and avatar uploads answer 409. The `dev` profile enables it with RustFS credentials and `create-bucket: true`; integration tests start a RustFS Testcontainer in `AbstractApiIntegrationTest`
- Avatars (`auth/internal/service/AvatarService`): the upload is decoded with ImageIO (PNG/JPEG/GIF, ≤ 4096 px per side checked from the header before decoding), center-cropped and re-encoded as a 256×256 PNG, which also drops EXIF. One object per user (`avatars/<userId>.png`) is overwritten on change, so no orphans; `users.avatar_updated_at` (V15) is both the "has avatar" flag and the URL version
- The S3 write happens before the DB update, outside any transaction; on delete the row is cleared first and a failed object delete is only logged
- `GET /api/v1/users/{id}/avatar` is public (an `<img src>` cannot carry the in-memory access token); `?v=` matching the current version → `Cache-Control: immutable`, otherwise `no-cache` + ETag/304. `AuthResponse.avatarUrl` carries the versioned URL; the frontend keeps it in `authStore` and renders it with `UserAvatar` (falls back to the initial)
- Upload limits: `spring.servlet.multipart` 2 MB file / 3 MB request; nginx `client_max_body_size 3m` on `/api/`

### Threading Model
- **Virtual threads (Project Loom)** enabled via `spring.threads.virtual.enabled: true`
- Tomcat uses `VirtualThreadExecutor` for all HTTP request threads
- `NotificationWorker` dispatches bounded batches on virtual threads; email retries use Spring Framework 7 `@Retryable` on `EmailService` (enabled by `@EnableResilientMethods`), whose backoff sleep parks the virtual thread
- gRPC: `GrpcServerConfig` supplies a virtual-thread `GrpcServerExecutorProvider` (not covered by `spring.threads.virtual.enabled`), and `FateGrpcService` passes a virtual-thread coroutine dispatcher to its base class — RPC bodies call blocking JPA directly, no `withContext` needed. Trace context follows coroutines via Spring Boot's `ObservationCoroutineContextServerInterceptor`
- Each outbox job targets one recipient, so a slow mailbox only holds up its own job; `EmailService` caps simultaneous SMTP sends with a semaphore (`app.mail.max-concurrent-sends`, default 4) held for a single attempt, not across retry backoff
- Don't expose an `Executor`/`ExecutorService` bean: Spring Boot would back off from its virtual-thread `applicationTaskExecutor` used by `@Async`

### Key Backend Services
- **DrawService**: core draw logic with SIMPLE / FAIR_ROTATION branching for both participants and options, by the vote's `drawTarget`. The winner comes from `WinnerPicker`: a fresh `SecureRandom` 32-byte seed per draw, candidates in canonical order (participant emails / option ids, sorted), index = SHA-256(seed ‖ counter) with rejection sampling (no modulo bias). Seed and candidates are stored in `draw_history.seed` / `candidates` (V22, not exposed by the API), so `candidates[WinnerPicker.index(seed, size)]` reproduces any recorded winner. `WinnerPickerTest` pins golden values — never change the algorithm. This proves reproducibility, not that the server didn't choose the seed (that would need commit-reveal)
- **VoteService**: CRUD for votes including creating `VoteOption` entities from request; `addOption()` / `removeOption()` for post-creation management; all vote mutations acquire the same pessimistic row lock; `@Version` additionally guards stale saves
- **Notifications** (module `notification`): `NotificationRecorder` (vote) persists one outbox job per recipient inside the vote transaction; `AccountService` (auth) records `EMAIL_VERIFICATION` / `PASSWORD_RESET` jobs whose payload holds the raw link token until delivery. Payloads are versioned DTOs (`NotificationPayloads.kt`), not the domain events. `NotificationWorker` claims jobs with `SKIP LOCKED`, delivers outside DB transactions, retries with exponential backoff and marks jobs failed after `app.notifications.max-attempts` (metric `notification.dead`), purging them after `failed-retention` (see `NotificationProperties`, backend/README.md). `NotificationAdapter` handles SMTP delivery and propagates failures to the worker.
- **Email budget**: `EmailBudget` caps the emails one user's actions trigger (invitations + draw-result recipients) to `app.notifications.email-budget-per-user` (`EMAIL_BUDGET_PER_USER`, 200) per rolling `email-budget-window` (24 h), recorded in the append-only `email_budget_ledger` (V17) under a per-user advisory lock — deleting a vote or participant frees nothing. Invitations beyond it are rejected (`TooManyRequestsException`, 429 / `RESOURCE_EXHAUSTED`, the whole create/add rolls back); a draw beyond it still happens, only its result emails are dropped (metric `notification.suppressed`). Entries past the window are purged hourly
- **EmailService**: sends styled HTML emails (dark theme) for vote invitations and draw results
- **FateGrpcService**: gRPC server implementation; uses `runCatching` + `StatusRuntimeException` for error mapping; maps `VoteOptionInfo` proto messages for options

### Error Handling
- Client-facing errors extend the sealed `shared.FateException` via one of five open categories: `BadRequestException` (400 / `INVALID_ARGUMENT`), `NotFoundException` (404 / `NOT_FOUND`), `ForbiddenException` (403 / `PERMISSION_DENIED`), `ConflictException` (409 / `FAILED_PRECONDITION`), `TooManyRequestsException` (429 / `RESOURCE_EXHAUSTED`, a used-up quota). Every one takes a mandatory `shared.ErrorCode` — the stable machine-readable reason, sent as the REST problem's `code` (`problem()` in `shared/Problems.kt` builds every error body) and as gRPC `ErrorInfo.reason` / the `error_code` of mutating responses. The message is only an English fallback: the frontend (`src/lib/errorMessages.ts`, a `Record` over the generated `ErrorCode` union — a new code fails the build) and the bot (`errorTexts` in `bot/internal/handler/errors.go`, `TestEveryErrorCodeHasAText`) show their own Russian text per code. Never rename or reuse a code; add it to `ErrorCode.kt` and the proto `ErrorCode` enum (`ERROR_CODE_` + name, next number) together — `ErrorMappingContractTest` checks they mirror each other. `ErrorCodesIntegrationTest` covers the codes end to end
- Mapping lives in exactly two places: `ErrorHandler` (REST) and `grpc/GrpcErrors.kt` (`grpcRead {}` / `grpcMutation {}`); `ErrorMappingContractTest` fails if a category is added without mapping it on both sides
- `ErrorHandler` extends `ResponseEntityExceptionHandler`, so Spring MVC errors (malformed JSON, bad path UUID, 405, unknown route) keep their 4xx status; bean-validation errors go through the `handleMethodArgumentNotValid` override (never add an `@ExceptionHandler` for an exception the base class already handles — startup fails on the ambiguous mapping)
- gRPC error contract is documented atop `proto/fate/v1/fate.proto`. Every expected status error carries `google.rpc.ErrorInfo{domain: "fate.v1", reason: <ErrorCode name>}` (e.g. `TELEGRAM_NOT_LINKED` with `NOT_FOUND`, which the bot tells apart from a missing vote)
- For business rules use `ensureValid(...)` / `ensureState(...)` and `throw NotFoundException(...)` — not `require`/`check`/`NoSuchElementException`. Stdlib exceptions are reserved for internal invariants and surface as 500 / `INTERNAL` without their message

### gRPC (Backend ↔ Bot)
- Proto source: `proto/fate/v1/fate.proto` (package `fate.v1`)
- Buf manages proto (`buf.yaml` + `buf.gen.yaml` at repo root)
- Backend generates Java/Kotlin stubs via `com.google.protobuf` Gradle plugin → `backend/build/generated/sources/proto/main/`
- Bot generates Go stubs via Buf → `bot/gen/fate/v1/`
- `FateGrpcService.kt` implements the service; `bot/internal/grpcclient/` wraps the Go stub
- **Transport security:** the channel is plaintext (no TLS) authenticated only by `SharedSecretAuthInterceptor` (constant-time compare of `x-grpc-shared-secret`). The server binds to `127.0.0.1` by default (`GRPC_BIND_ADDRESS`) so it is not reachable off-host; only set `0.0.0.0` on a trusted network. For a topology where the bot and backend run on different hosts, add mTLS.
- `getMyVotes` aggregates **all** of the caller's votes by paging server-side (up to `GRPC_MAX_PAGES` × 50), rather than returning only the first page.
- **Draw target:** `DrawTarget` enum, `CreateVoteRequest.draw_target` (UNSPECIFIED → inferred) and `GetVoteDetailsResponse.draw_target`; the bot shows it in `/vote` / `/newvote` and calls participants of an OPTIONS vote "Наблюдатели"
- **Key proto additions for vote options:**
  - `VoteOptionInfo { option_id, title }` — returned in `GetVoteDetailsResponse.options`
  - `CreateVoteRequest.options` — repeated string, optional; creates options server-side
  - `DrawVoteResponse.winner_option_title` / `DrawResultInfo.winner_option_title` — winner label when draw picks an option
  - Bot displays winner as: option title → display name → email (priority order)

### Telegram Bot Linking
1. User opens Settings page → clicks "Get link token" → `GET /api/v1/telegram/link-token`
2. Backend creates a `telegram_link_tokens` record (5-min expiry), returns the token
3. User sends `/link <token>` to the bot **in a private chat**
4. Bot calls `LinkTelegramAccount` gRPC → backend validates token, sets `users.telegram_id`
5. Bot notifies via Telegram; backend sends emails for invitations and draw results

### Testing
- **Backend**: MockK for unit tests. Tests live in `backend/src/test/kotlin/`. Coverage: JaCoCo 0.8.15, merged unit + integration report; generated protobuf/gRPC classes are excluded by the file names `generateProto` emitted (they share the `com.juncevich.fate.grpc` package with hand-written code). `check` enforces minimums of 95% lines / 85% branches (`jacocoTestCoverageVerification`) — cover new code with tests rather than lowering them
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
- Backend: `spring-boot-starter-opentelemetry` (Micrometer OTLP registry + OTel tracing bridge) → OTLP → OTel Collector; endpoint from `OTEL_EXPORTER_OTLP_ENDPOINT`. Log lines carry `[<traceId>-<spanId>]`
- Bot: `bot/internal/telemetry` (OTel Go SDK) → OTLP/gRPC → collector as `service.name=fate-bot`; a span per Telegram command with `otelgrpc` client child spans whose `traceparent` continues the trace in the backend; metrics `bot_commands_total{command,outcome}`, `bot_command_duration_seconds_*`. `OTEL_SDK_DISABLED=true` turns export off (see bot/README.md)
- OTel Collector (`infra/monitoring/otel-collector.yaml`): metrics → Mimir, traces → Tempo (`infra/monitoring/tempo.yaml`, single binary, local storage, 72 h retention), logs → Loki (OTLP to Loki's native `/otlp` endpoint). In minikube the collector also ships all pod logs (see Option 3)
- Metric names in Mimir (OTLP → Prometheus naming, `job="hand-of-fate-backend"`): `http_server_requests_milliseconds_*` (uri/method/status), `vote_created_total{mode}`, `vote_draw_performed_total{mode}`, `notification_failed_total{type}` / `notification_dead_total{type}` (created on the first failure), `jvm_*`, `hikaricp_*`, `process_cpu_usage`
- Grafana: provisioned datasources with fixed uids `mimir` / `loki` / `tempo` (Loki → Tempo via a derived field on the trace id in log lines, Tempo → Loki via `tracesToLogsV2`), and dashboards from `infra/monitoring/grafana/provisioning/dashboards/*.json` into the "Hand of Fate" folder — currently `hand-of-fate-overview.json` (HTTP, votes/draws, notifications, JVM, Hikari, Telegram bot commands, backend warn/error logs). Keep panel queries on the uids above; in minikube add a new dashboard file to the `grafana-dashboards` generator in `infra/k8s/components/observability/kustomization.yaml` too
- Tempo HTTP API: compose http://localhost:3200; `infra/k8s/check.sh` validates the Tempo config (`-config.verify=true`) and dashboard JSON

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
- V9–V13: vote version, draw-history indexes, `notification_outbox`, canonical participant emails
- V14: `notification_outbox.failed_at` dead-letter state + claim/purge indexes
- V15: `users.avatar_updated_at` (avatar version; image stored in object storage)
- V16: unlinks Telegram links to group chats (`telegram_id <= 0`) and adds `CHECK (telegram_id > 0)`
- V17: `email_budget_ledger` (per-user email budget, see Notifications)
- V18: refresh-token families — `refresh_tokens.family_id` + `revoked_at` (see Auth Flow)
- V19: drops indexes duplicated by UNIQUE constraints / composite indexes
- V20: `votes.draw_target` (`draw_target` enum), backfilled from whether a vote has options
- V21: option positions renumbered `0..n-1` + deferrable `UNIQUE (vote_id, position)`
- V22: `draw_history.seed` + `candidates TEXT[]` (draw audit; NULL for older draws)
- V23: `users.email_verified_at` (existing users backfilled as verified) + `email_tokens` (verification / password reset)
- V24: `email_suppressions` (addresses that unsubscribed)
- V25: FKs to `users(id)` get `ON UPDATE CASCADE` (a pre-Keycloak account is taken over by re-keying it to the Keycloak subject) + `deleted_accounts` (tombstones: a still-valid Keycloak token can't recreate a deleted account; also the retry queue of `IdentityDeletionWorker`)

### Key Environment Variables
| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/fate` | JDBC URL |
| `JWT_ACCESS_SECRET` | (dev default set) | Must be ≥256-bit in production |
| `MAIL_HOST` | `localhost` | SMTP host (Mailpit locally) |
| `MAIL_FROM` | — | Sender address; if blank, falls back to `MAIL_USERNAME`, then `noreply@handoffate.app` |
| `MAIL_MAX_CONCURRENT_SENDS` | `4` | Max simultaneous SMTP sends (`app.mail.max-concurrent-sends`) |
| `FRONTEND_URL` | `http://localhost:3000` | For CORS and email links |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` | OTLP HTTP endpoint |
| `TRACING_SAMPLING_PROBABILITY` | `0.1` (`1.0` in `dev`) | Fraction of requests traced |
| `BOT_TOKEN` | — | Required; from @BotFather |
| `GRPC_SERVER_ADDR` | `localhost:9090` | Bot → backend gRPC address |
| `GRPC_SHARED_SECRET` | (dev default set) | Shared secret authenticating bot → backend gRPC calls; must match on both sides, change before any real deployment |
| `UNSUBSCRIBE_SECRET` | (dev default set) | HMAC key of unsubscribe links; checked by `SecretsValidator` outside `dev`/`test`. Rotating it breaks the links in emails already sent |
| `GRPC_BIND_ADDRESS` | `127.0.0.1` | Interface the backend gRPC server binds to. Loopback by default (plaintext + shared-secret only); set `0.0.0.0` on a trusted internal network (Docker compose already does) |
| `JWT_ACCESS_TTL_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_TTL_DAYS` | `30` | Refresh token lifetime |
| `JWT_REFRESH_REUSE_GRACE_SECONDS` | `30` | How long a rotated refresh token is still accepted (concurrent refreshes) before a replay revokes the session |
| `LOG_LEVEL` | `info` | Bot log level |
| `SPRING_PROFILES_ACTIVE` | (none) | Set to `dev` for local runs (seeds demo user, disables Secure cookies, skips the production secret guard). Unset in production so `SecretsValidator` enforces non-default secrets |
| `REFRESH_COOKIE_SECURE` | `true` | Secure flag on the refresh cookie; forced to `false` by the `dev` profile for local HTTP |
| `SPRINGDOC_ENABLED` | `false` | Swagger UI + `/v3/api-docs`. Off in prod; the `dev` profile turns it on for local use |
| `app.rate-limit.capacity` / `app.rate-limit.window-seconds` | `10` / `60` | Per-IP request budget on `/api/v1/auth/{login,register,refresh}` |
| `app.rate-limit.login-failures-per-account` / `login-failure-window-seconds` | `10` / `900` | Failed logins per account (any IP) before login answers 429 |
| `EMAIL_BUDGET_PER_USER` | `200` | Notification emails one user's actions may trigger per 24 h (`app.notifications.email-budget-per-user`) |
| `S3_ENABLED` | `false` (`true` in `dev`) | Object storage for avatars; off → avatar upload/delete answer 409 |
| `S3_ENDPOINT` / `S3_REGION` / `S3_BUCKET` | `http://localhost:9000` / `us-east-1` / `fate` | S3-compatible store |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | — (`rustfsadmin` in `dev`) | Required when storage is enabled (startup fails otherwise) |
| `S3_PATH_STYLE` | `true` | Path-style addressing |
| `KEYCLOAK_URL` | `http://localhost:8180/auth` | Where the backend reaches Keycloak (JWKS, Admin API) |
| `KEYCLOAK_PUBLIC_URL` | `http://localhost:3000/auth` | Keycloak's public base URL; accepted tokens have `iss` = `<this>/realms/fate` |
| `KEYCLOAK_CLIENT_SECRET` | (dev default set) | Secret of the `fate-backend` service account (Admin API: end sessions, delete users); checked by `SecretsValidator` outside `dev`/`test` |

## CI/CD

Services run directly on Ubuntu via systemd (no Docker). Nginx serves the frontend and proxies `/api/` to the backend.

### CI environment

| Component | Version      |
|-----------|--------------|
| Runner OS | ubuntu-24.04 |
| Java      | 26 (Temurin) |
| Go        | from `go.mod` (1.27) |
| Node.js   | 26 (`frontend/.nvmrc`) |

### CI workflows (backend.yml / frontend.yml / bot.yml / perf.yml / simulation.yml / api.yml / k8s.yml)
Each project has its own workflow file, triggered on PR and push to `main` via path filters.
- `backend.yml`: `backend-test` (spotless + detekt + `./gradlew check`, uploads the JaCoCo report as artifact `backend-coverage`) → `backend-build` (artifact: `backend-jar`)
- `frontend.yml`: `frontend-test` (`npm run lint` + `npm run test`) → `frontend-build` (artifact: `frontend-dist`)
- `bot.yml`: `bot-test` (buf lint/breaking + gofmt + go vet + staticcheck + go test) → `build-bot` (artifact: `bot-binary`, built on `main` only)
- `perf.yml`: `perf-check` — spotless + detekt + `compileGatlingKotlin`. Lint/compile only; `gatlingRun` is a load test against a live backend and is run manually, not gated in CI.
- `simulation.yml`: `simulation-test` — generated-models check + gofmt + go vet + staticcheck + go test + build. The scenarios themselves (`go run ./cmd/simulate`) need a running backend and are not run in CI.
- `api.yml`: `api-check` on changes to `api/**` — Redocly lint of `api/openapi.yaml` and, on PRs, `oasdiff breaking` against the base branch (the REST analogue of `buf breaking`).
- `e2e.yml`: the whole stack on a throwaway minikube cluster (`K8S_OVERLAY=e2e ./k8s-start.sh`), then `infra/k8s/e2e.sh` (gateway smoke, every simulation scenario, mail delivery, metrics/logs in Mimir/Loki — also `make k8s-e2e`) and the Playwright tests in `e2e/`; uploads `playwright-report` on failure. The bot is not deployed.
- `k8s.yml`: `k8s-check` on changes to `infra/k8s/**`, `infra/nginx/**`, `infra/monitoring/**`, `docker-compose*.yml`, `k8s-*.sh` — runs `infra/k8s/check.sh` (also `make k8s-check`, needs Go + Docker, no cluster): renders every overlay (kustomize v5.8.2), `kubeconform -strict`, validates the OTel Collector (compose config and the k8s merge), Loki, Tempo and gateway nginx configs with the image versions the manifests pin, checks the Grafana dashboard JSON, requires every third-party image shared by `docker-compose*.yml` and the manifests to use the same tag (bump both together), and runs shellcheck on the k8s scripts.
- `frontend.yml` also runs `npm run check:api`; backend, frontend and simulation workflows all trigger on `api/openapi.yaml`.

### Deploy workflows (deploy-backend.yml / deploy-frontend.yml / deploy-bot.yml)
Each component has its own deploy workflow, normally triggered by its CI workflow completing and via `workflow_dispatch`.

> **Currently disabled:** there is no target environment, so the `workflow_run` trigger is commented out in all three deploy workflows — they run only on manual `workflow_dispatch`. Uncomment the trigger to re-enable auto-deploy.

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
2. Installs Java 26 (Temurin) and makes it the default `java` (also upgrades a server still on an older Java; `deploy-backend.yml` refuses to deploy to a server below Java 26)
3. Installs and enables Nginx
4. Installs PostgreSQL 17, creates `fate` DB user and `fate` database
5. Creates `fate` system user and `/opt/hand-of-fate/{backend,frontend,bot}/` directories
6. Registers `fate-backend` and `fate-bot` systemd units with `EnvironmentFile=/opt/hand-of-fate/.env`
7. Writes Nginx site config: SPA routing (`/`), API proxy (`/api/`), per-IP `limit_req` on `/api/v1/auth/`, security headers (CSP, HSTS, X-Frame-Options, nosniff, Referrer-Policy), static asset caching. **Swagger/OpenAPI is not proxied in prod** (also disabled server-side unless `SPRINGDOC_ENABLED=true`).
8. **TLS**: if `domain` + `email` inputs are provided, installs certbot and runs `certbot --nginx --redirect` (Let's Encrypt cert + 80→443 redirect). Without them it serves HTTP only and warns that the `Secure` refresh cookie will be dropped — provide a domain or terminate TLS upstream for a real deployment.
9. **Backups**: registers a `fate-db-backup.timer` (daily) running `/opt/hand-of-fate/scripts/backup-db.sh`, which `pg_dump`s the `fate` database to `/opt/hand-of-fate/backups/` (gzip) and prunes dumps older than 7 days. Local-disk only — copy `backups/` to offsite/object storage for real disaster recovery.

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
| `UNSUBSCRIBE_SECRET` | ≥256-bit random string signing the unsubscribe links of notification emails; on both deploy workflows (they share one `.env`) |

### Consistency regression checks

- `ConsistencyIntegrationTest` exercises concurrent token consumption and vote edits on PostgreSQL, history after winner deletion, canonical emails, uniqueness conflicts and pagination.
- `NotificationOutboxIntegrationTest` verifies rollback, persisted payloads, lease recovery, stale acknowledgments, per-recipient retries, retry delays, dead-lettering, retention purge and bounded dispatch.
- `ModularityTest` prohibits the vote module from depending on credential-bearing `auth.User`; use `auth.UserProfile`.
- REST `/votes/{id}/history/page` returns a stable page response; the legacy `/history` endpoint returns at most the latest 100 entries.
