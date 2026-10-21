# Dependency Updates — container images

Third-party images pinned in `docker-compose*.yml` **and** `infra/k8s/` (plus Dockerfiles and CI where noted). `infra/k8s/check.sh` fails when compose and the manifests disagree, and validates the Collector, Loki, Tempo and nginx configs with the pinned versions.

## 2026-10-21 — all images, majors in scope

Tags checked on Docker Hub (`tag_last_pushed`), quay.io and GitHub releases; stable tags at least two weeks old (pushed on or before 2026-10-07). The ranges are long, so the entries summarise what matters for this setup rather than every intermediate release.

### grafana/tempo `2.9.0 → 3.1.0` (major)
- Tempo 3 drops the ingester and compactor (monolithic mode runs live-store/block-builder in process, **no Kafka**); compaction and retention moved to the backend scheduler/worker. `tempo.yaml`: `compactor.compaction.block_retention` → `backend_worker.compaction.block_retention` (found with `-config.verify`; the old key makes 3.x refuse to start). On the existing volume 3.1.0 starts and logs only a warning about the 2.x WAL directory (`unowned file entry ignored during wal replay`). `3.1.1` is too young

### grafana/mimir `2.15.0 → 3.2.2` (major)
- Our single-binary config (`target: all`, filesystem storage, in-memory rings) starts unchanged on 3.x, also on the existing 2.x volume

### grafana/grafana `11.6.0 → 13.2.3` (two majors)
- 12 removed Angular panels — the dashboard only uses `timeseries`, `stat`, `logs` and `row`; provisioning of the datasources (fixed uids) and the dashboard works unchanged, all three datasource health checks OK. `13.2.4` is too young

### otel/opentelemetry-collector-contrib `0.123.0 → 0.162.0`
- 0.162 renamed component types; the old names still work as deprecated aliases. Configs moved to the new names: exporters `otlp/tempo` → `otlp_grpc/tempo`, `otlphttp/loki` → `otlp_http/loki`, `prometheusremotewrite` → `prometheus_remote_write`; receiver `filelog` → `file_log` (k8s). The `file_log` receiver's offsets are keyed by its id, so the first start after the rename re-reads the pods' logs once (duplicate old lines in a local Loki). `0.163.x` is too young

### grafana/loki `3.4.2 → 3.7.8`
- Config valid as is (`-verify-config`); OTLP ingestion on `/otlp` unchanged

### nginx `1.27.4-alpine → 1.31.6-alpine` (gateway, frontend image, compose)
- Mainline; `nginx -t` passes for the local, Tilt and e2e gateway configs

### postgres `17.4-alpine → 17.11-alpine` (compose, k8s StatefulSet and init containers, backend CI service)
- Minor releases only (bug and security fixes); data directory compatible. **18.x not taken**: a major Postgres version needs `pg_upgrade`/dump-restore of existing volumes, and production setup (`server-setup.yml`) installs 17 — a separate migration

### axllent/mailpit `v1.31.2 → v1.31.4`
- Patch releases

Checked and left unchanged: `quay.io/keycloak/keycloak` `26.8.0` (26.8.1, 2026-10-13, too young), `adorsys/keycloak-config-cli` `6.5.1-26` (latest), `rustfs/rustfs` `1.0.1` (1.0.2+ too young), `ghcr.io/navikt/mock-oauth2-server` `6.0.4` (6.0.5+ too young), floating bases `node:26-alpine`, `golang:1.27-alpine`, `eclipse-temurin:26-*`.

Verified: `infra/k8s/check.sh`; the whole stack redeployed on minikube (`K8S_OVERLAY=e2e ./k8s-start.sh`), `infra/k8s/e2e.sh` green (simulation 4/4, mail, backend metrics in Mimir 3, logs in Loki 3.7, traces in Tempo 3, Keycloak metrics/traces), Playwright 13/13 (shuffled), Grafana 13 datasources healthy, no deprecation warnings left in the Collector's log.
