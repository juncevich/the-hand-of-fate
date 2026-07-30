---
name: e2e-scenario
description: Use to run and interpret the Go simulation user-behaviour scenarios (session/simple/fair/options) against a local or remote backend — a functional end-to-end smoke test independent of unit tests.
---

# e2e-scenario

Runs `simulation/` — a Go end-to-end user-behaviour simulator that exercises real API flows against a live backend (no mocks, no fixtures; registers fresh random users each run).

## Prerequisites

A running backend reachable at the target URL (default `localhost:8080`). Use the `dev-environment` skill to get one up locally if needed.

## Running

```bash
cd simulation
go run ./cmd/simulate                          # all scenarios against localhost:8080
go run ./cmd/simulate -scenario simple         # just one: simple | fair | options | session
go run ./cmd/simulate -url https://host.com    # target a non-local backend (e.g. staging)
```

Build a binary if running repeatedly or in CI-like contexts:
```bash
go build -o fate-sim ./cmd/simulate
./fate-sim -scenario fair
```

## Scenarios

| Scenario | Covers |
|---|---|
| `session` | Auth lifecycle — register, login, refresh |
| `simple` | Options-based vote, SIMPLE mode draw |
| `fair` | FAIR_ROTATION mode + round tracking (every participant/option wins once before repeats) |
| `options` | Dynamic option add/remove on a vote |

## Interpreting failures

- Output on failure: `FAIL <scenario>: <error>`, process exits with code 1.
- Because each run registers fresh random users, failures are almost always either:
  1. A real regression in the corresponding backend flow (auth, draw, vote CRUD) — check backend logs for the same time window.
  2. A backend that isn't actually up / reachable at the target `-url` — verify with `curl <url>/actuator/health` or similar before assuming a functional bug.
- For `fair` scenario failures specifically, check `DrawService`'s round-tracking logic (`draw_history` + `currentRound`) — this is the most complex draw path and most likely target of a real bug.

## When to use this vs. backend unit tests

Unit/MockK tests validate logic in isolation; this validates the actual wire-level contract (REST + gRPC where applicable) end to end. Run this after backend API changes, before trusting a deploy, or when a bug report doesn't reproduce in unit tests.
