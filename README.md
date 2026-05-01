# The Hand of Fate

A voting and random selection application with fair rotation support. Users create votes, invite participants by email or define a list of named options, and let a random draw choose a winner. The **Fair Rotation** mode ensures every participant/option wins once per round before anyone wins again.

## System Architecture

```
  Browser (SPA)              Telegram
        │                       │
        │ HTTPS                 │ Telegram API (long-poll)
        ▼                       ▼
  ┌───────────┐         ┌───────────────┐
  │   Nginx   │         │  Bot  (Go)    │
  │  :80/443  │         │  bot/         │
  └─────┬─────┘         └───────┬───────┘
        │ /api/*                │ gRPC :9090
        │                       │
        └──────────┬────────────┘
                   ▼
        ┌──────────────────────┐
        │   Backend  (Kotlin)  │
        │   REST  :8080        │
        │   gRPC  :9090        │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐     ┌──────────────────────┐
        │     PostgreSQL       │     │   OTel Collector     │
        │       :5432          │     │  → Mimir / Loki      │
        └──────────────────────┘     └──────────────────────┘
```

## Features

- Create votes and invite participants by email
- Define named vote options (tasks, topics, items) as an alternative draw target
- Two draw modes: **Simple** (pure random) and **Fair Rotation** (everyone wins once before the cycle repeats)
- Telegram bot — manage votes without opening the web UI
- Email notifications for invitations and draw results
- Observability: metrics, logs, and traces via OpenTelemetry → Grafana
- Dark/light theme toggle

## Monorepo Structure

```
backend/    Kotlin 2.3.21 + Spring Boot 4.0.6 — REST API + gRPC server
frontend/   React 19 + TypeScript 6 + Vite 8 + Tailwind CSS 4
bot/        Go 1.25.0 — Telegram bot, gRPC client
proto/      Shared protobuf definitions (fate/v1/fate.proto)
infra/
  nginx/      Nginx reverse proxy config
  monitoring/ OTel Collector, Loki, Mimir, Grafana provisioning
  k8s/        Kubernetes manifests + Kustomize overlays (not active)
.github/
  workflows/  backend.yml, frontend.yml, bot.yml, deploy.yml, server-setup.yml
```

## Tech Stack

| Component    | Language / Runtime | Key Libraries                              |
|--------------|--------------------|--------------------------------------------|
| `backend/`   | Kotlin 2.3.21      | Spring Boot 4.0.6, gRPC 1.80, JJWT 0.13   |
| `frontend/`  | TypeScript 6.0.3   | React 19.2, Vite 8, TanStack Query 5       |
| `bot/`       | Go 1.25.0          | grpc 1.80, zap 1.27, viper 1.21            |
| DB           | PostgreSQL         | Flyway migrations (V1–V8)                  |
| Observability| —                  | OpenTelemetry, Mimir, Loki, Grafana        |

## Quick Start

```bash
cp .env.example .env   # set BOT_TOKEN
docker compose up -d
```

| Service   | URL                                   |
|-----------|---------------------------------------|
| Frontend  | http://localhost:3000                 |
| Backend   | http://localhost:8080                 |
| Swagger   | http://localhost:8080/swagger-ui.html |
| MailHog   | http://localhost:8025                 |
| Grafana   | http://localhost:3001  (admin/admin)  |

**Demo user** (seeded automatically): `admin@admin.com` / `admin`

## Development

Option 2 — infrastructure in Docker, apps native (hot-reload):

```bash
./dev-start.sh    # starts postgres + mailhog, then backend/frontend/bot natively
./dev-stop.sh     # stop infrastructure
```

### Backend
```bash
cd backend
./gradlew bootRun       # requires postgres on :5432
./gradlew test
./gradlew bootJar
```

### Frontend
```bash
cd frontend
npm install
npm run dev             # dev server on :3000, proxies /api → :8080
npm run dev:mock        # mock API — no backend needed
npm test
npm run build
```

### Proto (gRPC stubs)
```bash
make proto              # regenerate stubs for both bot and backend
make proto-bot          # Go stubs via Buf
make proto-backend      # Java/Kotlin stubs via Gradle
```

### Telegram Bot
```bash
cd bot
go run ./cmd/bot        # requires BOT_TOKEN + GRPC_SERVER_ADDR
go test ./...
```

## Telegram Bot Commands

| Command | Arguments | Description |
|---------|-----------|-------------|
| `/start`, `/help` | — | Welcome message and command list |
| `/link` | `<token>` | Link Telegram to app account (token from Settings page) |
| `/unlink` | — | Unlink account |
| `/votes` | — | List your votes with status and participant count |
| `/vote` | `<id>` | Vote details — title, mode, participants/options |
| `/newvote` | `<title> \| <emails> \| <mode> [\| <options>]` | Create a vote |
| `/draw` | `<id>` | Perform a draw (creator only) |
| `/result` | `<id>` | Last draw result |
| `/history` | `<id>` | Full draw history |

## Deployment

CI/CD via GitHub Actions — three separate pipelines (backend, frontend, bot) feed into a deploy pipeline that gates on all three passing for the same SHA, then deploys artifacts to an Ubuntu VPS via rsync and restarts systemd services.

See [CLAUDE.md](CLAUDE.md) for full architecture details, environment variables, and CI/CD documentation.

## License

MIT
