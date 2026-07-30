---
name: quality-check
description: Use to audit code quality across backend/frontend/bot/perf/simulation — runs static analysis, formatting, linting, and architecture-boundary checks, then reports concrete issues to fix (not just pass/fail).
---

# quality-check

Runs this monorepo's quality tooling per project and reports findings — distinct from `release-checklist` (which is a fast pre-merge CI-parity gate on changed files). This skill is a deeper, standalone audit: run it on demand, on a whole project, to surface real issues worth fixing, not just to get a green light.

## Scope

Ask or infer which project(s) to audit if not obvious from context: `backend`, `frontend`, `bot`, `perf`, `simulation`. Default to whatever the user is currently working in; audit all only if asked.

## Backend / perf (Kotlin + Gradle)

```bash
cd backend   # or perf
./gradlew detekt          # static analysis — read the actual findings, don't just report pass/fail
./gradlew spotlessCheck   # formatting drift (ktlint) — run spotlessApply to fix, don't hand-edit
./gradlew test            # regressions
./gradlew test --tests "*.ModularityTest"   # hexagonal module-boundary violations (backend only)
```
- Detekt findings live in `backend/build/reports/detekt/`; read the actual report, summarize real issues (complexity, unused code, naming) rather than just the pass/fail count.
- Watch specifically for violations of this repo's hexagonal convention: JPA annotations leaking into `domain/`, or one module's `internal/` package reached into from outside — `ModularityTest` catches structural cases but a manual grep for `import com.juncevich.fate.<othermodule>.internal` is a useful supplement.

## Frontend (React + TypeScript + Vite)

```bash
cd frontend
npm run lint       # ESLint
npm test           # Vitest
npm run build      # tsc via Vite build — catches type errors lint may not
```
- Read actual ESLint output, not just exit code — group findings by rule to spot systemic issues (e.g. repeated `any` usage, missing dependency arrays).
- Check for React Query / Zustand pattern drift: hooks should encapsulate query logic (`frontend/src/hooks/`), pages should not call `frontend/src/api/*` directly — grep for direct api imports in `src/pages/` as a smell check.

## Bot / simulation (Go)

```bash
cd bot   # or simulation
go vet ./...
gofmt -l .          # files needing formatting; should be empty
go test ./...
```
- `gofmt -l .` output is a list of non-conforming files — if non-empty, run `gofmt -w .` and note what changed.
- `go vet` findings are real bugs (suspicious constructs), not style — treat any output here as must-fix, not optional.

## Cross-cutting checks worth calling out

- **Secrets/config hygiene**: grep for hardcoded secrets or dev-default values (`JWT_ACCESS_SECRET`, `GRPC_SHARED_SECRET`) accidentally used outside `dev`/`test` profile guards.
- **Test coverage gaps**: for backend, confirm new services/controllers have corresponding MockK/standaloneSetup tests (see `backend-module-scaffold` skill for the expected pattern) rather than running a coverage tool that isn't configured in this repo.
- **Dependency staleness**: not part of this skill — use the `update-dependencies` skill instead.

## Reporting

Summarize per project: tool run, pass/fail, and the actual list of findings worth fixing (file:line where possible) — not just "detekt passed." If findings are trivial and mechanical (formatting), offer to auto-fix (`spotlessApply` / `gofmt -w`) rather than listing each one.
