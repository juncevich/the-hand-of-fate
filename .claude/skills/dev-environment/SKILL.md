---
name: dev-environment
description: Use to start, stop, or diagnose the local dev stack (backend/frontend/bot/postgres/mailhog) for The Hand of Fate — wraps dev-start.sh/dev-stop.sh/make targets and troubleshoots stuck ports or missing env vars.
---

# dev-environment

Manages the local development stack for this monorepo.

## Starting everything (native apps + Docker infra — preferred for hot reload)

```bash
cp .env.example .env          # first time only; add BOT_TOKEN if testing the bot
./dev-start.sh                # or: make dev-local
```
This starts Postgres + MailHog via `docker-compose.infra.yml`, then runs backend/frontend/bot natively with colored per-service logs. Bot startup is skipped automatically if `BOT_TOKEN` is unset.

Stop with:
```bash
./dev-stop.sh
```

## Infra only

```bash
make infra          # postgres + mailhog only
make infra-down      # stop infra
```

## Full Docker (no hot reload)

```bash
docker compose up -d
```

## Service URLs

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| MailHog | http://localhost:8025 |
| Grafana | http://localhost:3001 (admin/admin) |

## Default demo user (dev/test only)

`admin@admin.com` / `admin` — seeded by V7 migration, gated by the `seedDemoUser` Flyway placeholder (`dev`/`test` profiles only, never production).

## Diagnosing a stuck environment

1. **Port already in use** (3000 frontend, 8080 backend, 5432 postgres, 8025 mailhog):
   ```bash
   lsof -nP -iTCP:3000 -sTCP:LISTEN
   lsof -nP -iTCP:8080 -sTCP:LISTEN
   ```
   `dev-start.sh` is supposed to handle stale process cleanup itself — if it still fails, kill the stale PID manually and retry.
2. **Backend won't boot** — check `SPRING_PROFILES_ACTIVE=dev` is set (native run via `dev-start.sh` sets this); without it, `SecretsValidator` (`@Profile("!dev & !test")`) will abort startup if `JWT_ACCESS_SECRET` / `GRPC_SHARED_SECRET` are missing/short/default.
3. **Bot doesn't start** — confirm `BOT_TOKEN` is set in `.env`; `dev-start.sh` silently skips the bot otherwise.
4. **Frontend can't reach backend** — `npm run dev` proxies `/api` → `:8080`; confirm backend is actually up on 8080, not still starting (virtual-threads/Tomcat boot can take a few seconds).
5. **Frontend without a backend at all** — use `npm run dev:mock` (mock API, no backend needed).

## Running backend/frontend/bot independently (if not using dev-start.sh)

```bash
cd backend && ./gradlew bootRun     # needs postgres on :5432 and SPRING_PROFILES_ACTIVE=dev
cd frontend && npm run dev
cd bot && go run ./cmd/bot          # needs BOT_TOKEN and backend gRPC reachable
```
