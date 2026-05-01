# The Hand of Fate — Telegram Bot

Telegram-based interface for [The Hand of Fate](../README.md). Users can link their account, manage votes, trigger draws, and view results without opening the web UI.

## Architecture

```
cmd/bot/main.go
  ├── config.Load()        reads env vars via Viper
  ├── grpcclient.New()     insecure gRPC connection to backend :9090
  └── handler.New()
       └── handler.Run()   Telegram long-poll loop
            └── dispatch   one goroutine per message
                 └── gRPC  each call wrapped with 5 s context deadline
```

Each command handler calls `fate.v1.FateService` over gRPC. Proto definitions: `../proto/fate/v1/fate.proto`. Generated Go stubs: `gen/fate/v1/`.

## Commands

| Command | Arguments | Description |
|---------|-----------|-------------|
| `/start` | — | Welcome message |
| `/help` | — | Command reference |
| `/link` | `<token>` | Link Telegram to app account (token from Settings → Telegram Bot) |
| `/unlink` | — | Unlink account |
| `/votes` | — | List your votes with status and participant count |
| `/vote` | `<id>` | Vote details — title, mode, participants/options |
| `/newvote` | `<title> \| <emails> \| <mode> [\| <options>]` | Create a vote |
| `/draw` | `<id>` | Perform a draw (creator only) |
| `/result` | `<id>` | Last draw result |
| `/history` | `<id>` | Full draw history |

### `/newvote` examples

Draw from participants:
```
/newvote Team Lunch | alice@example.com, bob@example.com | fair
```

Draw from named options — leave emails empty:
```
/newvote Sprint Topic | | simple | Fix login bug, Refactor auth, Add dark mode
```

Modes: `simple` (random draw each time) or `fair` / `fair_rotation` (every participant/option wins once per round before the cycle repeats). When options are provided they take precedence over participants.

## Environment Variables

| Variable | Default | Required | Description |
|----------|---------|----------|-------------|
| `BOT_TOKEN` | — | **yes** | Telegram bot token from @BotFather |
| `GRPC_SERVER_ADDR` | `localhost:9090` | no | Backend gRPC address |
| `LOG_LEVEL` | `info` | no | Log level: info, debug, warn, error |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | no | OpenTelemetry collector (gRPC) |

## Running Locally

```bash
export BOT_TOKEN=your_token_here
go run ./cmd/bot
```

Or with Docker:

```bash
docker build -t fate-bot .
docker run --rm -e BOT_TOKEN=your_token_here -e GRPC_SERVER_ADDR=host:9090 fate-bot
```

Full stack from repo root:

```bash
cp .env.example .env   # set BOT_TOKEN
docker compose up -d
```

## Running Tests

```bash
cd bot
go test ./...
```

No external services required — all gRPC and Telegram interactions are faked via interfaces.

## Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| `telegram-bot-api/telegram-bot-api/v5` | v5.5.1 | Telegram API |
| `spf13/viper` | v1.21.0 | Config via env vars |
| `go.uber.org/zap` | v1.27.1 | Structured logging |
| `google.golang.org/grpc` | v1.80.0 | gRPC client |
| `google.golang.org/protobuf` | v1.36.11 | Proto runtime |
