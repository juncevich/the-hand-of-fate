---
name: dev-environment
description: Use to start, stop, or diagnose the local dev stack (backend/frontend/bot/postgres/mailpit/keycloak/monitoring) for The Hand of Fate — in either setup, Docker (dev-start.sh / docker compose) or local Kubernetes (minikube via k8s-start.sh, Tilt via make k8s-dev) — and to troubleshoot stuck ports, missing env vars, or a broken cluster.
---

# dev-environment

Manages the local development stack for this monorepo. There are two independent setups; they use different host ports and can run side by side.

| Setup | Start | Stop | Hot reload |
|---|---|---|---|
| Docker infra + native apps (preferred) | `./dev-start.sh` (`make dev-local`) | `./dev-stop.sh` | yes (Gradle/Vite/go run) |
| Everything in Docker | `docker compose up -d` (`make dev`) | `docker compose down` | no |
| minikube, prebuilt images | `./k8s-start.sh` (`make k8s-up`) | `./k8s-stop.sh` (`make k8s-down`) | no |
| minikube + Tilt | `make k8s-dev` | Ctrl+C, then `make k8s-dev-down` | yes (Tilt) |

## Docker setups

```bash
cp .env.example .env          # first time only; add BOT_TOKEN if testing the bot
./dev-start.sh                # or: make dev-local
```
Starts Postgres + Mailpit + RustFS + Keycloak (with `keycloak-config`, which applies realm `fate` from `infra/keycloak/`) via `docker-compose.infra.yml`, then runs backend/frontend/bot natively with colored per-service logs. Bot startup is skipped automatically if `BOT_TOKEN` is unset. `./dev-stop.sh` stops it.

Infra only: `make infra` / `make infra-down`. Full Docker without hot reload: `docker compose up -d` (also starts the monitoring stack: OTel Collector, Loki, Mimir, Tempo, Grafana).

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| Mailpit | http://localhost:8025 |
| Keycloak | admin console http://localhost:8180/auth/admin (admin/admin); realm endpoints on the SPA origin http://localhost:3000/auth/ (Vite proxy) |
| RustFS console | http://localhost:9001 (rustfsadmin/rustfsadmin) |
| Grafana | http://localhost:3001 (admin/admin; full compose only) |

## Kubernetes setups (minikube profile `hand-of-fate`)

Needs `brew install minikube` (and `tilt` for `make k8s-dev`). Everything targets kubectl context `hand-of-fate` explicitly; other contexts are never touched.

```bash
./k8s-start.sh                   # create/resume cluster, build images, deploy, wait (~first run: several minutes)
./k8s-start.sh --skip-build      # re-apply manifests only
./k8s-start.sh --only backend    # rebuild + restart selected apps (backend,frontend,bot)
./k8s-start.sh --cluster-only    # just create/resume the cluster
./k8s-stop.sh                    # stop the cluster — data kept
./k8s-stop.sh --delete           # delete the cluster with all data and images
make k8s-status                  # pods, services, PVCs
make k8s-dev / make k8s-dev-down # Tilt loop / remove what Tilt deployed (volumes kept)
make k8s-check                   # static checks, what CI's k8s.yml runs (Go + Docker, no cluster)
K8S_OVERLAY=e2e ./k8s-start.sh && make k8s-e2e   # end-to-end checks, what CI's e2e.yml runs
```

| Service | URL |
|---|---|
| App (gateway: SPA + `/api`) | http://localhost:30080 |
| Mailpit | http://localhost:30025 |
| Keycloak | http://localhost:30080/auth/admin/ (admin/admin), through the gateway |
| RustFS console | http://localhost:30901/rustfs/console/ |
| Postgres | `localhost:30432` (fate/fate, db fate) |
| Grafana (metrics, pod logs, traces) | http://localhost:30300 (admin/admin) |
| Tilt UI | http://localhost:10350 |
| Swagger | Tilt: http://localhost:18080/swagger-ui.html; otherwise `kubectl --context hand-of-fate -n hand-of-fate port-forward svc/backend 18080:8080` |

Manifests: `infra/k8s/base` (apps + nginx gateway) → `overlays/local` (Postgres, RustFS, Mailpit, Keycloak DB + dev realm, monitoring component, NodePorts) → `overlays/tilt` (Vite dev server, no CSP) / `overlays/e2e` (raised auth rate limit). Secrets come from `.env` via `overlays/local/write-secrets.sh` (`.secrets.env`, and `.oauth.env` for social login).

## Default demo user (dev/test only)

`admin@admin.com` / `admin` — seeded by V7 migration, gated by the `seedDemoUser` Flyway placeholder (`dev`/`test` profiles only, never production). This is the app's own (legacy) login.

In Keycloak (realm `fate`, `infra/keycloak/realm-dev/`) the same address has the password `admin123` — the realm's policy needs 8+ characters. The legacy login goes away with the Keycloak migration (docs/plans/keycloak-auth.md).

## Diagnosing the Docker setups

1. **Port already in use** (3000 frontend, 8080 backend, 5432 postgres, 8025 mailpit):
   ```bash
   lsof -nP -iTCP:3000 -sTCP:LISTEN
   lsof -nP -iTCP:8080 -sTCP:LISTEN
   ```
   `dev-start.sh` is supposed to handle stale process cleanup itself — if it still fails, kill the stale PID manually and retry.
2. **Backend won't boot** — check `SPRING_PROFILES_ACTIVE=dev` is set (native run via `dev-start.sh` sets this); without it, `SecretsValidator` (`@Profile("!dev & !test")`) will abort startup if `JWT_ACCESS_SECRET` / `GRPC_SHARED_SECRET` are missing/short/default.
3. **Bot doesn't start** — confirm `BOT_TOKEN` is set in `.env`; `dev-start.sh` silently skips the bot otherwise. Without a collector, set `OTEL_SDK_DISABLED=true` to skip telemetry export (its errors are debug-level only anyway).
4. **Frontend can't reach backend** — `npm run dev` proxies `/api` → `:8080`; confirm backend is actually up on 8080, not still starting.
5. **Keycloak** — `docker compose -f docker-compose.infra.yml logs keycloak keycloak-config`. `keycloak-config` exits 0 when the realm was applied; rerun it after editing `infra/keycloak/realm*/` with `docker compose -f docker-compose.infra.yml up keycloak-config`. In minikube: `kubectl --context hand-of-fate -n hand-of-fate logs job/keycloak-config` (k8s-start.sh recreates the Job on every run).
6. **Frontend without a backend at all** — use `npm run dev:mock` (mock API, no backend needed).

## Diagnosing the Kubernetes setups

1. **Start with** `make k8s-status`, then `kubectl --context hand-of-fate -n hand-of-fate describe pod <pod>` / `logs <pod> --all-containers` for anything not `Running 1/1`.
2. **`WARNING: ... doesn't run the docker runtime`** or **`port N isn't published`** from `k8s-start.sh` — the cluster predates a change (Tilt needs the docker runtime; host ports are fixed when the node is created). Recreate: `./k8s-stop.sh --delete && ./k8s-start.sh` (wipes cluster data).
3. **Image pulls / builds fail with `lookup auth.docker.io ... no such host`** — Docker Hub is unreachable from this network (seen without VPN). Nothing in the repo can fix it: restore access and retry. A fresh cluster needs every third-party image pulled again; images already in the host's Docker cache can be copied in with `minikube -p hand-of-fate image load <image>`.
4. **App not answering on :30080 right after a resume** — `k8s-start.sh` waits for a 401 from `/api/v1/votes` through the gateway; if it times out, check the backend pod (Postgres init container, startup probe) and the gateway pod.
5. **Simulation fails with 429 / 503** — the backend allows 10 auth requests/min per IP (429) and the gateway nginx 5/min (503). Run it against the backend directly via port-forward and deploy the e2e overlay (`K8S_OVERLAY=e2e ./k8s-start.sh --skip-build`), as `infra/k8s/e2e.sh` does.
6. **Tilt says it must target `hand-of-fate`** — start it via `make k8s-dev` (passes `--context`). Another Tilt instance already on :10350 means one is running (possibly in another terminal) — don't start a second one or redeploy underneath it without stopping it first.
7. **Empty Grafana panels** — bot panels stay empty unless the bot runs (needs `BOT_TOKEN`); `notification_*` panels stay empty until a delivery fails; Loki pod logs exist only in minikube (the collector tails `/var/log/pods`).

## Running backend/frontend/bot independently (if not using dev-start.sh)

```bash
cd backend && ./gradlew bootRun     # needs postgres on :5432 and SPRING_PROFILES_ACTIVE=dev
cd frontend && npm run dev
cd bot && go run ./cmd/bot          # needs BOT_TOKEN and backend gRPC reachable
```
