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
  workflows/ CI (ci.yml) + deploy (deploy.yml)
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
npm run dev      # dev server on :3000 (proxies /api → :8080)
npm test         # run vitest
npm run build    # production build
```

### Bot (Go)
```bash
cd bot
# Generate proto stubs first (requires protoc + plugins):
protoc -I ../proto \
  --go_out=gen --go_opt=paths=source_relative \
  --go-grpc_out=gen --go-grpc_opt=paths=source_relative \
  ../proto/fate.proto

go run ./cmd/bot          # run
go test ./...             # test
go build -o bot ./cmd/bot # build binary
```

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
- Backend generates Java/Kotlin stubs via `com.google.protobuf` Gradle plugin; stubs land in `backend/build/generated/source/proto/main/`
- Bot generates Go stubs via `protoc`; stubs live in `bot/gen/fate/v1/`
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
- Grafana: pre-provisioned datasources (Mimir, Loki); add dashboards under `infra/monitoring/grafana/provisioning/dashboards/`

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

## CI/CD
- `ci.yml`: runs on all PRs and pushes to `main` — tests all three services, builds Docker images on `main`
- `deploy.yml`: deploys to `staging` on `main` push, to `production` on `v*` tags
- Images: `ghcr.io/juncevich/fate-{backend,frontend,bot}:{sha}`
- K8s: kustomize overlays at `infra/k8s/overlays/{staging,production}/`
- Secrets expected: `KUBE_CONFIG_STAGING`, `KUBE_CONFIG_PRODUCTION` in GitHub environments
