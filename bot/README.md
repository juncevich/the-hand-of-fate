# The Hand of Fate — Telegram Bot

Telegram-based front end for [The Hand of Fate](../README.md) lottery application. Users can link their account, create votes, trigger draws, and view results without opening the web UI.

## Architecture

```
cmd/bot/main.go
  └─ config.Load()          reads env vars via Viper
  └─ grpcclient.New()       insecure gRPC connection to backend
  └─ handler.New()          message dispatcher
       └─ handler.Run()     long-poll loop, one goroutine per message
            └─ gRPC calls   each wrapped with 5 s context deadline
```

Each command handler calls the backend over gRPC (`fate.v1.FateService`). Proto definitions live in `../proto/fate/v1/fate.proto`; generated Go stubs are in `gen/fate/v1/`.

## Commands

| Command | Description | Arguments |
|---|---|---|
| `/start` | Welcome message | — |
| `/help` | Command reference | — |
| `/link` | Link Telegram to app account | `<token>` from Settings → Telegram Bot |
| `/unlink` | Unlink account | — |
| `/votes` | List your votes with status and participant count | — |
| `/newvote` | Create a vote | `<title> \| <email1,email2> \| <simple\|fair>` |
| `/vote` | Vote details and participant list | `<id>` |
| `/draw` | Perform a draw (creator only) | `<id>` |
| `/result` | Last draw result | `<id>` |
| `/history` | Full draw history | `<id>` |

### `/newvote` example

```
/newvote Team Lunch | alice@example.com, bob@example.com | fair
```

Modes: `simple` (random draw each time) or `fair` / `fair_rotation` (everyone wins once per round before the cycle repeats).

## Environment Variables

| Variable | Default | Required | Description |
|---|---|---|---|
| `BOT_TOKEN` | — | **yes** | Telegram bot token from @BotFather |
| `GRPC_SERVER_ADDR` | `localhost:9090` | no | Backend gRPC address |
| `LOG_LEVEL` | `info` | no | Log level (info, debug, warn, error) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | no | OpenTelemetry collector endpoint |

## Running Locally

```bash
export BOT_TOKEN=your_token_here
go run ./cmd/bot
```

Or with Docker (multi-stage build, final image is Alpine):

```bash
docker build -t fate-bot .
docker run --rm -e BOT_TOKEN=your_token_here -e GRPC_SERVER_ADDR=host:9090 fate-bot
```

For the full stack (backend + bot + infrastructure):

```bash
# from repo root
cp .env.example .env   # add BOT_TOKEN
docker compose up -d
```

## Running Tests

```bash
cd bot
go test ./...
```

No external services required — all gRPC and Telegram interactions are faked via interfaces.
