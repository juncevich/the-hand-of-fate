# The Hand of Fate

A voting and random selection application with fair rotation support. Users create votes, invite participants by email, and let a random draw choose a winner. The **Fair Rotation** mode ensures every participant wins once per round before anyone wins again.

## Features

- Create votes and invite participants by email
- Two draw modes: **Simple** (pure random) and **Fair Rotation** (every participant wins before the cycle repeats)
- Telegram bot integration — link your account and manage votes directly in Telegram
- Email notifications for invitations and draw results
- Observability stack: metrics, logs, and traces via OpenTelemetry
- Dark/light theme toggle

## Architecture

```
backend/     Kotlin + Spring Boot 4.0.6, PostgreSQL, gRPC server
frontend/    React 19 + TypeScript + Vite + Tailwind CSS 4 + shadcn/ui
bot/         Go Telegram bot, gRPC client to backend
proto/       Shared protobuf definitions (fate.proto)
infra/
  nginx/     Nginx reverse proxy configs
  monitoring/ OTel Collector, Loki, Mimir, Grafana provisioning
  k8s/       Kubernetes manifests + kustomize overlays (staging/production)
```

## Tech Stack

| Component  | Language / Runtime | Key Dependencies                         |
|------------|--------------------|------------------------------------------|
| `backend/` | Kotlin 2.3.21      | Spring Boot 4.0.6, gRPC 1.80, JJWT 0.13 |
| `frontend/`| TypeScript 6.0.3   | React 19.2, Vite 8, TanStack Query 5     |
| `bot/`     | Go 1.25.0          | grpc 1.80, zap 1.27, viper 1.21          |

## Quick Start

```bash
cp .env.example .env   # add BOT_TOKEN
docker compose up -d
```

| Service   | URL                                    |
|-----------|----------------------------------------|
| Frontend  | http://localhost:3000                  |
| Backend   | http://localhost:8080                  |
| Swagger   | http://localhost:8080/swagger-ui.html  |
| MailHog   | http://localhost:8025                  |
| Grafana   | http://localhost:3001 (admin/admin)    |

## Development

### Backend
```bash
cd backend
./gradlew bootRun       # run locally (needs postgres on :5432)
./gradlew test          # all tests
./gradlew bootJar       # build fat JAR
```

### Frontend
```bash
cd frontend
npm install
npm run dev             # dev server with proxy to :8080
npm run dev:mock        # dev server with mock API (no backend needed)
npm test                # run tests
npm run build           # production build
```

### Proto (gRPC stubs)
```bash
make proto              # regenerate stubs for both bot and backend
make proto-bot          # bot only (uses Buf)
make proto-backend      # backend only (uses Gradle)
```

### Telegram Bot
```bash
cd bot
go run ./cmd/bot        # run (requires GRPC_SERVER_ADDR and BOT_TOKEN)
go test ./...           # tests
```

**Bot commands:** `/link <token>`, `/votes`, `/draw <id>`, `/result <id>`, `/unlink`

## Deployment

CI/CD via GitHub Actions — three separate pipelines (backend, frontend, bot) feed into a deploy pipeline that gates on all three passing for the same SHA, then SCPs artifacts to an Ubuntu VPS and restarts systemd services.

See [CLAUDE.md](CLAUDE.md) for full architecture details, environment variables, and CI/CD documentation.

## License

MIT
