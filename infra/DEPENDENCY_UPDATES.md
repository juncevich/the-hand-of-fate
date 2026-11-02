# Dependency Updates — container images

Third-party images pinned in `docker-compose*.yml` **and** `infra/k8s/` (plus Dockerfiles and CI where noted). `infra/k8s/check.sh` fails when compose and the manifests disagree, and validates the Collector, Loki, Tempo and nginx configs with the pinned versions.

## 2026-11-02 — all images and CI tools, majors in scope

Checked against Docker Hub tags, the upstream GitHub release APIs, npm and Go module registries. Per user request, majors included; stable releases selected without the previous release-age cutoff. Compose, Kubernetes, Dockerfiles and Testcontainers pins move together.

### PostgreSQL `17.11-alpine → 18.6-alpine` (major)

- PostgreSQL 18 adds asynchronous I/O, B-tree skip scans and UUIDv7 generation; the application does not depend on those features. JDBC/Flyway tests pass on the new server.
- Each 18.x release's changes: [18.0](https://www.postgresql.org/docs/18/release-18.html), [18.1](https://www.postgresql.org/docs/18/release-18-1.html), [18.2](https://www.postgresql.org/docs/18/release-18-2.html), [18.3](https://www.postgresql.org/docs/18/release-18-3.html), [18.4](https://www.postgresql.org/docs/18/release-18-4.html), [18.5](https://www.postgresql.org/docs/18/release-18-5.html), [18.6](https://www.postgresql.org/docs/18/release-18-6.html).
- Existing clusters need an explicit data migration. Compose explicitly retains its old `PGDATA`; Kubernetes already sets it. This avoids the PostgreSQL 18 image's new default directory silently selecting an empty location. [Migration procedure](POSTGRES_UPGRADE.md). No existing database or volume was migrated/deleted during this update.
- Fresh VPS setup installs PostgreSQL 18; existing installations remain untouched. CI, schema export and integration-test containers use the same 18.6 image.

### nginx `1.31.6-alpine → 1.31.8-alpine`

- Gateway and frontend runtime pins updated together. Changes in [1.31.7 and 1.31.8](https://nginx.org/en/CHANGES). All three gateway variants pass `nginx -t` on the new image.

### Mailpit `1.31.4 → 1.33.0`

- Development SMTP/mailbox image refreshed in both Compose files and Kubernetes. Intermediate changes: [1.31.5](https://github.com/axllent/mailpit/releases/tag/v1.31.5), [1.31.6](https://github.com/axllent/mailpit/releases/tag/v1.31.6), [1.32.0](https://github.com/axllent/mailpit/releases/tag/v1.32.0), [1.33.0](https://github.com/axllent/mailpit/releases/tag/v1.33.0).

### RustFS `1.0.1 → 1.0.4`

- Object-storage image updated in Compose, Kubernetes and Testcontainers. Avatar upload/read/delete integration tests pass. Intermediate changes: [1.0.2](https://github.com/rustfs/rustfs/releases/tag/1.0.2), [1.0.3](https://github.com/rustfs/rustfs/releases/tag/1.0.3), [1.0.4](https://github.com/rustfs/rustfs/releases/tag/1.0.4).

### OTel Collector Contrib `0.162.0 → 0.164.0`

- Collector binaries/CA bundle refreshed; 0.164.0 adds the partitioning processor to Contrib and includes several previously Contrib-only components in core. Existing pipelines require no configuration changes.
- Intermediate changes: [0.163.0](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases/tag/v0.163.0), [0.163.1](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases/tag/v0.163.1), [0.163.2](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases/tag/v0.163.2), [0.164.0](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases/tag/v0.164.0).
- Both the standalone Compose configuration and the merged Kubernetes configuration pass the new image's `validate` command.

### Loki `3.7.8 → 3.7.9`

- Fixes concurrent WAL append ordering and tightens request-size limits; dependency fixes also included. [Release changes](https://github.com/grafana/loki/releases/tag/v3.7.9). Existing configuration passes `-verify-config`.

### Mimir `3.2.2 → 3.2.4`

- Single-binary image refreshed; [3.2.3 changes](https://github.com/grafana/mimir/releases/tag/mimir-3.2.3), [3.2.4 changes](https://github.com/grafana/mimir/releases/tag/mimir-3.2.4). Kubernetes schema validation passes; a live metrics ingestion test was not run.

### Tempo `3.1.0 → 3.1.1`

- Rebuilt on Go 1.27.3 with its security fixes. [Release changes](https://github.com/grafana/tempo/releases/tag/v3.1.1). Existing configuration passes `-config.verify=true`.

### Grafana `13.2.3 → 13.2.5`

- Dashboard image refreshed; [13.2.4 changes](https://github.com/grafana/grafana/releases/tag/v13.2.4), [13.2.5 changes](https://github.com/grafana/grafana/releases/tag/v13.2.5). Provisioned dashboard JSON validation passes; a live Grafana browser session was not tested.

### Keycloak `26.8.0 → 26.8.1`

- Includes fixes to broker account linking, logout-token validation and fine-grained admin permissions. [Release changes](https://github.com/keycloak/keycloak/releases/tag/26.8.1). Dockerfile and integration test pins agree; the real Keycloak authentication/admin smoke test passes.
- keycloak-config-cli remains `6.5.1-26`, latest stable of its compatible image line; applied realm configuration still works.

### Mock OAuth2 server `6.0.4 → 6.1.4`

- Intermediate changes: [6.1.0](https://github.com/navikt/mock-oauth2-server/releases/tag/6.1.0), [6.1.1](https://github.com/navikt/mock-oauth2-server/releases/tag/6.1.1), [6.1.2](https://github.com/navikt/mock-oauth2-server/releases/tag/6.1.2), [6.1.3](https://github.com/navikt/mock-oauth2-server/releases/tag/6.1.3), [6.1.4](https://github.com/navikt/mock-oauth2-server/releases/tag/6.1.4).
- 6.1.4 binds authorization codes to their issuing issuer. The e2e provider already uses one issuer for authorization and token exchange. The browser social-login scenario was not executed in this pass.

### Gradle Actions `v5 → v6` (major)

- Backend/perf CI now follows the current v6 action line (latest release 6.4.1). [Migration/release history](https://github.com/gradle/actions/releases). Existing setup-gradle inputs remain unchanged; hosted CI execution cannot be reproduced locally.

### Kustomize `5.8.2 → 5.8.3`

- [Release changes](https://github.com/kubernetes-sigs/kustomize/releases/tag/kustomize/v5.8.3). All three overlays render; all 176 rendered resources pass strict schema validation.

### ShellCheck `0.10.0 → 0.11.0`

- Adds checks for redirected output capture, unused functions and BusyBox glob compatibility; revises several optional diagnostics. [Release changes](https://github.com/koalaman/shellcheck/releases/tag/v0.11.0). Repository Kubernetes scripts pass on the updated image.

### kubectl `1.37.0 → 1.37.2`

- E2E CI remains on minikube 1.39.0's Kubernetes minor line, with the latest client patch: [1.37.1](https://github.com/kubernetes/kubernetes/releases/tag/v1.37.1), [1.37.2](https://github.com/kubernetes/kubernetes/releases/tag/v1.37.2).

### Redocly CLI `2.55.0 → 2.72.1`; oasdiff `1.33.0 → 1.35.0`

- API CI tool pins updated to the npm latest / stable Go module release. Redocly's [complete release history](https://github.com/Redocly/redocly-cli/blob/main/packages/cli/CHANGELOG.md) covers the intermediate 2.56–2.72 patches.
- oasdiff [1.34.0](https://github.com/oasdiff/oasdiff/releases/tag/v1.34.0) and [1.35.0](https://github.com/oasdiff/oasdiff/releases/tag/v1.35.0): the latter makes diff output deterministic and expands OAuth-flow and sunset-date checks.

### Checked, left unchanged

- Node 26 image/runtime line (stable patch 26.13.0), Go 1.27 image line (stable patch 1.27.3), Alpine 3.24 line (3.24.3), Java 26 toolchain/runtime line, minikube 1.39.0, kubeconform 0.8.0, keycloak-config-cli 6.5.1-26 and appleboy/ssh-action 1.2.5.
- Existing major-tagged GitHub Actions checkout/setup-node/setup-go/setup-java/upload-artifact/download-artifact and buf-action already resolve within their current latest major lines. Gradle Actions was the available major-line change.
- Floating runtime image tags remain floating within their configured line, following the existing repository convention. Prerelease/nightly/edge tags were excluded.

Verified: `make k8s-check` (all overlays, 176 schemas, OTel/Loki/Tempo/nginx configs, dashboard JSON, image parity, ShellCheck). Backend integration tests pass on PostgreSQL 18.6/RustFS 1.0.4/Keycloak 26.8.1. Existing Compose/minikube application stacks were not redeployed. Redocly 2.72.1 validates the contract (two warnings about missing explicit 4xx responses on public unsubscribe operations); oasdiff 1.35.0 reports no differences for the unchanged contract. Disposable containers confirm that both configured PGDATA paths detect a PostgreSQL 17 version marker, skip initialization and preserve that marker. `make db-schema` applies all 26 migrations on PostgreSQL 18.6 and exports the schema successfully.

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
