# Simulation

Go-based user-behaviour simulator for the Hand of Fate backend. Each run signs up fresh random users on Keycloak's own pages (docs/adr/0001) and walks them through realistic API flows to verify that all endpoints work end-to-end.

## Purpose

| Tool | What it tests |
|------|---------------|
| `perf/` (Gatling) | Throughput, latency, concurrency under load |
| `simulation/` (this) | Correct behaviour of the full user journey, one step at a time |

The simulator is not a load tool — it runs scenarios sequentially with a single HTTP client per user and is meant to be used as a quick functional smoke check against a real backend.

## Structure

```
simulation/
  cmd/simulate/main.go          — entry point; -url, -app, -scenario and -mailpit flags (new accounts are
                                  confirmed through the verification email Keycloak sends, read from Mailpit)
  internal/
    client/
      models.gen.go             — API DTOs generated from api/openapi.yaml (`go generate ./...`)
      models.go                 — go:generate directive
      oapi-codegen.yaml         — oapi-codegen config (models only, plain-string IDs)
      client.go                 — typed HTTP client wrapping every REST endpoint (bearer token from Keycloak)
      client_test.go            — unit tests for every client method (httptest)
    keycloak/
      browser.go                — Browser: signs up/in on Keycloak's pages like a browser (client fate-spa,
                                  Authorization Code + PKCE), silent renewal (prompt=none), sign-out
      browser_test.go           — Browser against the fake below
      keycloaktest/server.go    — fake Keycloak for tests (forms, redirects, PKCE check, mailed links)
    mailbox/
      mailpit.go                — reads the links Keycloak mails from Mailpit's HTTP API
    scenario/
      helpers.go                — random name / email / option generators
      helpers_test.go           — unit tests for all generator functions
      signup.go                 — SignUp(), SignedUpClient(): a fresh user signed up through Keycloak
      session_lifecycle.go      — full auth lifecycle scenario
      session_lifecycle_test.go — SignUp + SessionLifecycleScenario against fake Keycloak and backend
      simple_vote.go            — SIMPLE vote with named options scenario
      simple_vote_test.go       — winnerLabel tests + SimpleVoteScenario mock tests
      fair_rotation.go          — FAIR_ROTATION vote scenario
      fair_rotation_test.go     — FairRotationScenario mock tests (draw loop, errors)
      options_vote.go           — dynamic option management scenario
      options_vote_test.go      — OptionsVoteScenario mock tests
      testutil_test.go          — shared test helpers (logger, client, writeJSON, fakeBackend)
```

## Prerequisites

- Go 1.27+
- A running stack — `./dev-start.sh` (see repo root): the backend on :8080, the SPA's origin on :3000 with Keycloak under `/auth` (Vite proxies it), Mailpit on :8025 for the verification emails Keycloak sends

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

# The minikube stack: backend, Keycloak and the SPA behind the gateway
go run ./cmd/simulate -url http://localhost:30080 -app http://localhost:30080 -mailpit http://localhost:30025

# Non-local target
go run ./cmd/simulate -url https://your-server.com -app https://your-server.com -mailpit …

# Build a standalone binary
go build -o fate-sim ./cmd/simulate
./fate-sim -scenario all
```

### Flags

| Flag | Default | Description |
|------|---------|-------------|
| `-url` | `http://localhost:8080` | Backend base URL |
| `-app` | `http://localhost:3000` | The SPA's origin; Keycloak is reached under its `/auth` |
| `-scenario` | `all` | Which scenario to run: `all`, `simple`, `fair`, `options`, `session` |
| `-mailpit` | `http://localhost:8025` | Mailpit base URL (verification emails) |

### Exit codes

- `0` — all selected scenarios passed
- `1` — at least one scenario failed (error is printed to stderr)

## Scenarios

### `session` — Auth lifecycle with Keycloak

Tests the complete authentication flow for a single user:

1. Sign up on Keycloak's registration form, open the verification link from Mailpit, set the password (with email verification on, Keycloak asks for it only after the link); the backend accepts the token (`GET /users/me`)
2. A wrong password is rejected on the login form
3. Sign in in a second browser
4. Renew silently with the session cookie (`prompt=none`), as the SPA's hidden iframe does
5. Fetch a Telegram link token (requires valid auth)
6. Sign out in the first browser (RP-initiated logout) → its silent renewal fails
7. `POST /auth/logout-all` through the backend → the second browser's renewal fails too

### `simple` — SIMPLE vote with options

Tests a vote whose draw pool is named options (not participants):

1. Sign up through Keycloak
2. Create a SIMPLE vote with 4 random options
3. List votes, fetch vote detail
4. Perform a draw → verify winner is one of the options
5. Fetch draw history
6. Reopen the vote, draw again
7. Close the vote
8. Delete the vote

### `fair` — FAIR_ROTATION vote with participants

Tests the fair-rotation algorithm and participant management:

1. Sign up through Keycloak
2. Create a FAIR_ROTATION vote with 3 random participant emails
3. Draw repeatedly until `newRoundStarted = true` (all participants have won once); reopen between draws
4. Add a new participant after the round ends
5. Reopen and draw again with the expanded pool
6. Remove a participant
7. Fetch full draw history
8. Delete the vote

### `options` — Dynamic option management

Tests adding and removing options via the REST API after vote creation:

1. Sign up through Keycloak
2. Create a vote with **no** options
3. Add 3 options one by one via `POST /{id}/options`
4. Fetch vote detail to confirm options are stored
5. Draw (picks from options)
6. Remove one option via `DELETE /{id}/options/{optionId}`
7. Reopen and draw again
8. Delete the vote

## HTTP Client

`internal/client/client.go` wraps every REST endpoint in a typed method and sends the Keycloak access token as a bearer token. Signing up and in happen in `internal/keycloak` (`Browser`), which keeps Keycloak's session cookies like a browser tab and exchanges the authorization code with PKCE as the SPA's client `fate-spa`.

### Covered endpoints

| Method | Path | Client method |
|--------|------|---------------|
| GET | `/api/v1/users/me` | `GetProfile` |
| POST | `/api/v1/auth/logout-all` | `LogoutAll` |
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
| `internal/client` | — | Every client method: correct HTTP method + path, JSON decoding, error propagation on 4xx/5xx |
| `internal/keycloak` | — | Sign-up with the password set after the mailed link, wrong credentials, silent renewal until sign-out — against `keycloaktest` |
| `internal/scenario` (helpers) | 12 | `randomEmail` format + uniqueness, `randomName` word structure, `randomVoteTitle`, `randomOptions` count/uniqueness/clamping, `ptr[T]` |
| `internal/scenario` (scenarios) | 37 | `winnerLabel` priority logic; happy-path + error cases for all four scenarios — `SimpleVoteScenario`, `FairRotationScenario`, `OptionsVoteScenario`, `SessionLifecycleScenario` (including round-flip loop via `atomic.Int32`, sessions surviving logout-all) |

## Adding a New Scenario

1. Create `internal/scenario/my_scenario.go` with a function `MyScenario(c *client.Client, log *zap.Logger) error`.
2. Register it in `cmd/simulate/main.go` inside the `run` block with a short name.
3. Use `SignedUpClient` if the scenario needs a fresh authenticated user, or bring your own `*client.Client`.

## Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| `github.com/google/uuid` | v1.6.0 | Random unique email suffixes |
| `go.uber.org/zap` | v1.28.0 | Structured logging |
