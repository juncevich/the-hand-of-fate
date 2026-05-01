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

Manifests are maintained but **not active in current production** — production runs as systemd services on a VPS. Kept for future migration.

### Workloads

| Component | Replicas | Strategy    | Memory      | CPU           |
|-----------|----------|-------------|-------------|---------------|
| backend   | 2        | RollingUpdate | 512Mi–1Gi | 250m–1000m    |
| frontend  | 2        | RollingUpdate | 64Mi–128Mi | 50m–200m     |
| bot       | **1**    | **Recreate** | 64Mi–128Mi | 50m–200m     |

The bot must be a single replica — Telegram long-polling is not idempotent.

### Structure

```
k8s/
├── namespace.yaml
├── backend/       deployment, service, hpa, configmap
├── frontend/      deployment, service
├── bot/           deployment
├── postgres/      statefulset
├── ingress/       ingress routing
└── overlays/
    ├── staging/   Kustomize overlay
    └── production/ Kustomize overlay
```

### Applying

```bash
kubectl apply -k infra/k8s/overlays/staging
kubectl apply -k infra/k8s/overlays/production
```

Backend probes: `GET /actuator/health/readiness` and `/actuator/health/liveness`

Prometheus scrape: annotation `prometheus.io/scrape: "true"`, path `/actuator/prometheus`, port `8080`
