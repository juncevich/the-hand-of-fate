---
name: proto-sync
description: Use after editing proto/fate/v1/fate.proto — regenerates gRPC stubs for both backend (Kotlin) and bot (Go), and verifies both sides consume any new fields/messages consistently.
---

# proto-sync

Keeps `backend` (Kotlin/Java stubs) and `bot` (Go stubs) in sync after a change to the shared proto definition.

## When to use

Any edit to `proto/fate/v1/fate.proto` — new RPC, new message field, renamed field, new enum value.

## Steps

1. **Read the diff** in `proto/fate/v1/fate.proto` first — know exactly which messages/RPCs changed.
2. **Regenerate stubs** from repo root:
   ```bash
   make proto              # runs proto-bot + proto-backend
   ```
   - `make proto-backend` — Java/Kotlin stubs via Gradle (`com.google.protobuf` plugin) → `backend/build/generated/source/proto/main/`
   - `make proto-bot` — Go stubs via Buf → `bot/gen/fate/v1/`
   - If `make proto-bot` fails with missing plugins, run `make install-proto-tools` first (installs `protoc-gen-go` / `protoc-gen-go-grpc`).
3. **Update the backend consumer**: `FateGrpcService.kt` implements the service — any new RPC or field needs a corresponding case there (uses `runCatching` + `StatusRuntimeException` for error mapping).
4. **Update the bot consumer**: `bot/internal/grpcclient/` wraps the generated Go stub — mirror new fields/RPCs here, and in whatever bot command surfaces them (see `bot/internal/...` command handlers referenced in CLAUDE.md's Telegram Bot Commands table).
5. **Check both directions of data mapping** — e.g. when `VoteOptionInfo` or similar nested messages change, confirm both the Kotlin mapper (`backend`) and Go struct usage (`bot`) handle the new/removed field, including nil/null cases.
6. **Compile both sides**:
   ```bash
   (cd backend && ./gradlew compileKotlin)
   (cd bot && go build ./...)
   ```
7. **Run relevant tests**: backend gRPC service tests, and `bot` tests that touch the changed RPC (`cd bot && go test ./...`).

## Gotchas

- The bind address / shared-secret auth (`SharedSecretAuthInterceptor`) is unaffected by proto changes but don't forget it exists if you're adding a new RPC — it must flow through the existing interceptor, not bypass it.
- Backend generation requires the Gradle build; bot generation requires Buf + protoc plugins — regenerating only one side is a common source of drift, always run both.
