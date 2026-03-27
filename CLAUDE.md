# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**The Hand of Fate** — a voting/selection application with fair rotation support. Users create votes, invite participants by email, and let a random draw choose a winner. The "Fair Rotation" mode ensures every participant wins once per round before anyone wins again.

## Monorepo Structure

```
backend/     Kotlin + Spring Boot 3.4.4, PostgreSQL, gRPC server
frontend/    React 19 + TypeScript + Vite + Tailwind CSS 4 + shadcn/ui
bot/         Golang Telegram bot, gRPC client to backend
proto/       Shared protobuf definitions (fate.proto)
infra/
  nginx/     Nginx reverse proxy configs
  monitoring/ OTel Collector, Loki, Mimir, Grafana provisioning
  k8s/       Kubernetes manifests + kustomize overlays (staging/production)
.github/
  workflows/ backend.yml + frontend.yml + bot.yml + deploy.yml + server-setup.yml
```

## Common Commands

### Local dev (full stack)
```bash
cp .env.example .env          # add BOT_TOKEN
docker compose up -d
```
- Frontend:  http://localhost:3000
- Backend:   http://localhost:8080
- Swagger:   http://localhost:8080/swagger-ui.html
- MailHog:   http://localhost:8025
- Grafana:   http://localhost:3001  (admin/admin)

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

### Telegram Bot Commands
| Command | Description |
|---|---|
| `/start`, `/help` | Welcome message and command list |
| `/link <token>` | Link Telegram to app account (token from Settings page) |
| `/votes` | List your votes with status and participant count |
| `/draw <id>` | Perform a draw for a vote (creator only) |
| `/result <id>` | Show last draw result for a vote |
| `/unlink` | Unlink Telegram account |

## Architecture

### Auth Flow
- Registration/login returns `accessToken` (JWT, 15 min) + `refreshToken` (UUID, 30 days)
- `refreshToken` is stored hashed in `refresh_tokens` table
- Frontend keeps `accessToken` in Zustand (memory only); `refreshToken` is sent via JSON body (not httpOnly cookie in current impl)
- On 401, Axios interceptor silently calls `/api/v1/auth/refresh` and retries once

### Vote Modes
- **SIMPLE**: random draw from all participants, no history tracking
- **FAIR_ROTATION**: tracks `draw_history` per round; only participants who haven't won in `currentRound` are eligible. When all have won, `currentRound` increments and the cycle restarts

### gRPC (Backend ↔ Bot)
- Proto source: `proto/fate.proto`
- Buf is used for proto management (`buf.yaml` + `buf.gen.yaml` at repo root)
- Backend generates Java/Kotlin stubs via `com.google.protobuf` Gradle plugin; stubs land in `backend/build/generated/source/proto/main/`
- Bot generates Go stubs via Buf (`buf generate`); stubs live in `bot/gen/fate/v1/`
- `FateGrpcService.kt` in the backend implements the service; `bot/internal/grpcclient/` wraps the Go stub

### Telegram Bot Linking
1. User opens Settings page → clicks "Get link token" → calls `GET /api/v1/telegram/link-token`
2. Backend creates a `telegram_link_tokens` record (5-min expiry), returns the token
3. User sends `/link <token>` to the bot
4. Bot calls `LinkTelegramAccount` gRPC → backend validates token, sets `users.telegram_id`
5. Bot notifies via Telegram; backend sends emails for invitations and draw results

### Observability
- Backend: Micrometer + `micrometer-tracing-bridge-otel` → OTLP → OTel Collector
- OTel Collector: metrics → Mimir, logs → Loki
- Custom metrics: `vote.created{mode}`, `vote.draw.performed{mode,round}`, `vote.participants.count`
- Grafana: pre-provisioned datasources (Mimir, Loki); add dashboard JSON files under `infra/monitoring/grafana/provisioning/dashboards/` (directory exists, no dashboards shipped yet)

### Database Migrations
Flyway, files in `backend/src/main/resources/db/migration/`:
- V1: users
- V2: votes (enum types: vote_mode, vote_status)
- V3: vote_participants
- V4: draw_history
- V5: refresh_tokens
- V6: telegram_link_tokens

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

> **Note:** `infra/k8s/` contains Kubernetes manifests with kustomize overlays (staging/production), but the active deployment is systemd-based. K8s manifests are prepared for future migration.

### CI воркфлоу (backend.yml / frontend.yml / bot.yml)
Каждый проект — отдельный yml файл. Запускаются на PR и push в `main` по path-фильтрам.
Каждый файл содержит две джобы: test → build.
- `backend.yml`: `backend-test` → `backend-build` (артефакт `backend-jar`)
- `frontend.yml`: `frontend-test` → `frontend-build` (артефакт `frontend-dist`)
- `bot.yml`: `bot-test` → `build-bot` (артефакт `bot-binary`, только на `main`)

### deploy.yml
Триггерится при завершении любого из трёх CI воркфлоу, а также через `workflow_dispatch`.

**Джоба `gate`** — проверяет через GitHub API что все три CI успешно прошли для одного SHA. Если нет — пропускает деплой (`ready=false`); деплой произойдёт когда завершится последний из трёх.

**Джоба `deploy`** — скачивает артефакты из соответствующих run-id каждого воркфлоу, копирует на сервер, перезапускает сервисы.

**Flow:**
1. Downloads the three artifacts from the CI run
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
