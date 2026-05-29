# Simulation

Go-based user-behaviour simulator for the Hand of Fate backend. Each run registers fresh random users and walks them through realistic API flows to verify that all endpoints work end-to-end.

## Purpose

| Tool | What it tests |
|------|---------------|
| `perf/` (Gatling) | Throughput, latency, concurrency under load |
| `simulation/` (this) | Correct behaviour of the full user journey, one step at a time |

The simulator is not a load tool — it runs scenarios sequentially with a single HTTP client per user and is meant to be used as a quick functional smoke check against a real backend.

## Structure

```
simulation/
  cmd/simulate/main.go          — entry point; -url and -scenario flags
  internal/
    client/
      models.go                 — all API DTOs (Auth, Vote, Draw, Telegram)
      client.go                 — typed HTTP client wrapping every REST endpoint
      client_test.go            — unit tests for every client method (httptest)
    scenario/
      helpers.go                — random name / email / option generators
      helpers_test.go           — unit tests for all generator functions
      register_login.go         — RegisterAndLogin(), LoginExisting() helpers
      session_lifecycle.go      — full auth lifecycle scenario
      session_lifecycle_test.go — SessionLifecycleScenario mock tests (happy path + error cases)
      simple_vote.go            — SIMPLE vote with named options scenario
      simple_vote_test.go       — winnerLabel tests + SimpleVoteScenario mock tests
      fair_rotation.go          — FAIR_ROTATION vote scenario
      fair_rotation_test.go     — FairRotationScenario mock tests (draw loop, errors)
      options_vote.go           — dynamic option management scenario
      options_vote_test.go      — OptionsVoteScenario mock tests
      testutil_test.go          — shared test helpers (logger, client, writeJSON)
```

## Prerequisites

- Go 1.25+
- A running backend — start with `docker compose up -d` or `./dev-start.sh` (see repo root)

## Running

```bash
# All scenarios against the local backend
cd simulation
go run ./cmd/simulate

# Single scenario
go run ./cmd/simulate -scenario simple
go run ./cmd/simulate -scenario fair
go run ./cmd/simulate -scenario options
go run ./cmd/simulate -scenario session

# Non-local target
go run ./cmd/simulate -url https://your-server.com

# Build a standalone binary
go build -o fate-sim ./cmd/simulate
./fate-sim -scenario all
```

### Flags

| Flag | Default | Description |
|------|---------|-------------|
| `-url` | `http://localhost:8080` | Backend base URL |
| `-scenario` | `all` | Which scenario to run: `all`, `simple`, `fair`, `options`, `session` |

### Exit codes

- `0` — all selected scenarios passed
- `1` — at least one scenario failed (error is printed to stderr)

## Scenarios

### `session` — Auth lifecycle

Tests the complete authentication flow for a single user:

1. Register a new account
2. Attempt login with wrong password → expect rejection
3. Login with correct password
4. Refresh the access token
5. Fetch a Telegram link token (requires valid auth)
6. Logout
7. Confirm that the used refresh token is now invalid

### `simple` — SIMPLE vote with options

Tests a vote whose draw pool is named options (not participants):

1. Register + login
2. Create a SIMPLE vote with 4 random options
3. List votes, fetch vote detail
4. Perform a draw → verify winner is one of the options
5. Fetch draw history
6. Reopen the vote, draw again
7. Close the vote
8. Delete the vote

### `fair` — FAIR_ROTATION vote with participants

Tests the fair-rotation algorithm and participant management:

1. Register + login
2. Create a FAIR_ROTATION vote with 3 random participant emails
3. Draw repeatedly until `newRoundStarted = true` (all participants have won once); reopen between draws
4. Add a new participant after the round ends
5. Reopen and draw again with the expanded pool
6. Remove a participant
7. Fetch full draw history
8. Delete the vote

### `options` — Dynamic option management

Tests adding and removing options via the REST API after vote creation:

1. Register + login
2. Create a vote with **no** options
3. Add 3 options one by one via `POST /{id}/options`
4. Fetch vote detail to confirm options are stored
5. Draw (picks from options)
6. Remove one option via `DELETE /{id}/options/{optionId}`
7. Reopen and draw again
8. Delete the vote

## HTTP Client

`internal/client/client.go` wraps every REST endpoint in a typed method. The cookie jar is enabled so the `fate_refresh_token` httpOnly cookie is handled automatically by `net/http`. The refresh token value is also extracted from `Set-Cookie` and returned in `AuthResponse.RefreshToken` for scenarios that need it explicitly.

### Covered endpoints

| Method | Path | Client method |
|--------|------|---------------|
| POST | `/api/v1/auth/register` | `Register` |
| POST | `/api/v1/auth/login` | `Login` |
| POST | `/api/v1/auth/refresh` | `Refresh` |
| POST | `/api/v1/auth/logout` | `Logout` |
| POST | `/api/v1/votes` | `CreateVote` |
| GET | `/api/v1/votes` | `ListVotes` |
| GET | `/api/v1/votes/{id}` | `GetVote` |
| DELETE | `/api/v1/votes/{id}` | `DeleteVote` |
| POST | `/api/v1/votes/{id}/participants` | `AddParticipant` |
| DELETE | `/api/v1/votes/{id}/participants/{email}` | `RemoveParticipant` |
| POST | `/api/v1/votes/{id}/options` | `AddOption` |
| DELETE | `/api/v1/votes/{id}/options/{optionId}` | `RemoveOption` |
| POST | `/api/v1/votes/{id}/draw` | `Draw` |
| POST | `/api/v1/votes/{id}/reopen` | `Reopen` |
| POST | `/api/v1/votes/{id}/close` | `Close` |
| GET | `/api/v1/votes/{id}/history` | `GetHistory` |
| GET | `/api/v1/telegram/link-token` | `GetLinkToken` |
| DELETE | `/api/v1/telegram/unlink` | `UnlinkTelegram` |

## Testing

The package has unit tests that run without a backend — all HTTP interactions go through `net/http/httptest` servers.

```bash
# Run all tests
cd simulation
go test ./...

# With verbose output
go test -v ./...

# Single package
go test ./internal/client/...
go test ./internal/scenario/...
```

### What is covered

| Package | Tests | What is verified |
|---------|-------|-----------------|
| `internal/client` | 31 | Every client method: correct HTTP method + path, JSON decoding, refresh-token cookie extraction, error propagation on 4xx/5xx |
| `internal/scenario` (helpers) | 12 | `randomEmail` format + uniqueness, `randomName` word structure, `randomVoteTitle`, `randomOptions` count/uniqueness/clamping, `ptr[T]` |
| `internal/scenario` (scenarios) | 37 | `winnerLabel` priority logic; happy-path + error cases for all four scenarios — `SimpleVoteScenario`, `FairRotationScenario`, `OptionsVoteScenario`, `SessionLifecycleScenario` (including round-flip loop via `atomic.Int32`, logout-failure non-fatal path) |

## Adding a New Scenario

1. Create `internal/scenario/my_scenario.go` with a function `MyScenario(c *client.Client, log *zap.Logger) error`.
2. Register it in `cmd/simulate/main.go` inside the `run` block with a short name.
3. Use `RegisterAndLogin` if the scenario needs a fresh authenticated user, or bring your own `*client.Client`.

## Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| `github.com/google/uuid` | v1.6.0 | Random unique email suffixes |
| `go.uber.org/zap` | v1.28.0 | Structured logging |
