# Proto

Shared protobuf definitions for the gRPC channel between the backend and the Telegram bot.

**Source:** `fate/v1/fate.proto` — package `fate.v1`, service `FateService`

## Service Overview

```
  Bot (Go)                        Backend (Kotlin)
  bot/internal/grpcclient/        grpc/FateGrpcService.kt
         │
         │  gRPC :9090
         │
         ├─ LinkTelegramAccount ──────► validate token → set users.telegram_id
         ├─ UnlinkTelegramAccount ────► clear users.telegram_id
         │
         ├─ GetMyVotes ───────────────► list votes by telegram_id
         ├─ CreateVote ───────────────► create vote + options
         ├─ GetVoteDetails ───────────► vote metadata + participants + options
         │
         ├─ DrawVote ─────────────────► run draw → winner (email or option title)
         ├─ GetLastDrawResult ────────► last DrawHistory entry
         └─ GetVoteHistory ───────────► all DrawHistory entries for a vote
```

## Service Methods

| Method | Request → Response | Description |
|--------|--------------------|-------------|
| `LinkTelegramAccount` | token → ok/error | Validate one-time token, bind Telegram ID |
| `UnlinkTelegramAccount` | telegram_id → ok | Remove Telegram binding |
| `GetMyVotes` | telegram_id → `[]Vote` | List votes owned by the caller |
| `CreateVote` | title, emails, mode, options → vote_id | Create a vote with optional named options |
| `GetVoteDetails` | vote_id → `Vote` + participants + options | Full vote info |
| `DrawVote` | vote_id → winner | Perform a draw; returns winner name or option title |
| `GetLastDrawResult` | vote_id → `DrawResultInfo` | Last draw result |
| `GetVoteHistory` | vote_id → `[]DrawResultInfo` | All draw results |

## Key Messages

| Message | Fields |
|---------|--------|
| `Vote` | id, title, description, status (`PENDING`/`DRAWN`/`CLOSED`), mode (`SIMPLE`/`FAIR_ROTATION`), participant_count |
| `VoteOptionInfo` | option_id, title |
| `DrawResultInfo` | winner_email, winner_display_name, winner_option_title, round, drawn_at |

Winner display priority (bot): `winner_option_title` → `winner_display_name` → `winner_email`

## Regenerating Stubs

```bash
# From repo root
make proto            # both targets
make proto-backend    # Java/Kotlin stubs via Gradle plugin
make proto-bot        # Go stubs via Buf (requires protoc-gen-go + protoc-gen-go-grpc)

make install-proto-tools   # install Go proto plugins
```

| Target | Output | Committed? |
|--------|--------|------------|
| Backend (Java/Kotlin) | `backend/build/generated/source/proto/main/` | No — generated at build time |
| Bot (Go) | `bot/gen/fate/v1/` | Yes |

## Tooling

- `buf` manages linting and Go generation — config in `buf.yaml` + `buf.gen.yaml` at repo root
- Backend uses the `com.google.protobuf` Gradle plugin for Java/Kotlin stub generation
