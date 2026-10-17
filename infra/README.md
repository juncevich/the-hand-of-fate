# Infra

Infrastructure configuration: Nginx reverse proxy, observability stack, and Kubernetes manifests.

## Structure

```
infra/
├── nginx/        Nginx config — used in production (systemd VPS deployment)
├── monitoring/   OTel Collector, Mimir, Loki, Grafana — local + server observability
└── k8s/          Kubernetes manifests + Kustomize overlays (not active in production)
```

---

## Nginx (`nginx/`)

Nginx runs directly on the VPS (not in Docker). It terminates HTTPS, serves the SPA, and proxies API and gRPC traffic.

```
Client
  │
  ▼
Nginx :80/:443
  ├── /                    → frontend static files  (try_files → index.html for SPA routing)
  ├── /api/                → backend :8080          (proxy_pass, X-Real-IP forwarded)
  ├── /api/v1/auth/*       → backend :8080          (rate-limited: 5 req/min, burst 10)
  ├── /swagger-ui/         → backend :8080
  └── /v3/api-docs         → backend :8080
```

Gzip compression enabled for text assets.

---

## Monitoring (`monitoring/`)

Started with `docker compose up -d` (included in the root `docker-compose.yml`).

```
Backend / Bot
     │
     │ OTLP  gRPC :4317 / HTTP :4318
     ▼
OTel Collector
     ├── metrics ──► Mimir   :9009   (Prometheus-compatible remote-write)
     └── logs    ──► Loki    :3100

Grafana :3001
     ├── datasource: Mimir
     └── datasource: Loki
```

| Component      | Port | Credentials |
|----------------|------|-------------|
| OTel Collector | 4317 (gRPC), 4318 (HTTP) | — |
| Mimir          | 9009 | — |
| Loki           | 3100 | — |
| Grafana        | 3001 | admin / admin |

Custom backend metrics: `vote.created{mode}`, `vote.draw.performed{mode,round}`, `vote.participants.count`

To add a dashboard: drop a JSON file into `monitoring/grafana/provisioning/dashboards/`.

---

## Kubernetes (`k8s/`)

Local Kubernetes only (minikube profile `hand-of-fate`); there is no staging/production cluster — production
runs as systemd services on a VPS. Start/stop, Tilt and host ports are described in the root `CLAUDE.md`
(Option 3).

### Structure

```
k8s/
├── base/                      backend, frontend, bot, gateway (nginx reusing infra/nginx/nginx.conf), namespace
├── components/observability/  OTel Collector DaemonSet, Loki, Mimir, Tempo, Grafana (kustomize Component)
├── overlays/
│   ├── local/                 Postgres, RustFS, Mailpit, dev config/secrets, NodePorts
│   ├── e2e/                   local + raised per-IP auth rate limit (CI end-to-end job)
│   └── tilt/                  dev images for `make k8s-dev`
├── check.sh                   static checks run by CI (`make k8s-check`)
└── e2e.sh                     end-to-end checks against the running stack (`make k8s-e2e`)
```

Every workload runs one replica. The bot must stay single and uses the `Recreate` strategy — Telegram
long-polling is not idempotent — and the backend's in-memory rate limits and notification worker also
assume a single instance.

Backend probes: `GET /actuator/health/readiness` and `/actuator/health/liveness`. Metrics are pushed over
OTLP to the collector; the backend exposes no scrape endpoint.
