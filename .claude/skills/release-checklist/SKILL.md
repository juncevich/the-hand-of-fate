---
name: release-checklist
description: Use before pushing/merging to main, or when asked "is this ready to ship" — runs the same checks CI enforces across backend, frontend, bot, proto and the Kubernetes/compose infra so failures are caught locally instead of in the pipeline.
---

# release-checklist

Runs the local equivalent of what `backend.yml` / `frontend.yml` / `bot.yml` / `k8s.yml` CI jobs check, per component touched.

## 1. Figure out what changed

```bash
git status
git diff --stat main...HEAD
```
Only run the checks for components actually touched (backend / frontend / bot / proto / perf / simulation / infra) — no need to run everything for a docs-only change.

## 2. Backend (if `backend/` or `proto/` changed)

```bash
cd backend
./gradlew spotlessCheck    # formatting — CI will fail on drift; run spotlessApply locally if it fails
./gradlew detekt           # static analysis
./gradlew test             # full unit test suite
```
If `proto/` changed, also run the `proto-sync` skill first — CI regenerates stubs, stale checked-in generated code isn't the actual gate but consumers using outdated fields will fail compilation/tests.

## 3. Frontend (if `frontend/` changed)

```bash
cd frontend
npm run lint
npm test
npm run build     # catches TS errors that vitest alone won't
```

## 4. Bot (if `bot/` or `proto/` changed)

```bash
cd bot
gofmt -l . | grep -v '^gen/'          # must print nothing
go vet ./...
go run honnef.co/go/tools/cmd/staticcheck@latest ./...
go test ./...
go build -o /dev/null ./cmd/bot
```

## 5. Simulation (if `simulation/` or backend API behavior changed)

Not part of CI gating but a good smoke test before a risky merge — requires a running backend:
```bash
cd simulation
go run ./cmd/simulate     # runs all scenarios: session, simple, fair, options
```
Exits 1 and prints `FAIL <scenario>: <error>` on first failure.

## 6. Module boundaries (backend only, if package structure changed)

```bash
cd backend && ./gradlew test --tests "*.ModularityTest"
```

## 7. Kubernetes / compose infra (if `infra/k8s/`, `infra/nginx/`, `infra/monitoring/`, `docker-compose*.yml` or `k8s-*.sh` changed)

```bash
make k8s-check     # = infra/k8s/check.sh: overlays render, kubeconform, collector/Loki/Tempo/nginx
                   #   configs, dashboard JSON, compose↔k8s image versions, shellcheck (Go + Docker)
```
A third-party image bumped in `docker-compose*.yml` must be bumped in the manifests too (and vice versa) — the check fails otherwise.

`e2e.yml` (full stack on minikube + simulation) also runs for backend/frontend/simulation changes. Locally, only when a cluster is affordable: `K8S_OVERLAY=e2e ./k8s-start.sh && make k8s-e2e` — needs Docker Hub access.

## 8. Deploy-path sanity (only if `.github/workflows/` or `infra/` changed)

- Confirm `deploy-*.yml` still references the correct artifact name and systemd service.
- Confirm any new required env var is added both to `CLAUDE.md`'s Key Environment Variables table and to the GitHub Secrets list, and that `server-setup.yml` / `.env` writing steps in the deploy workflow stay consistent.

## Summary format

Report pass/fail per component actually checked — don't claim a component is "clean" if its checks weren't run.
