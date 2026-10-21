# Dependency Updates

## 2026-10-21 — all dependencies, majors in scope

Checked against the npm registry; stable releases at least two weeks old, majors in scope.

### @playwright/test `1.63.0 → 1.64.0` (pinned exactly, as before)
- 1.64: `page.webmcp` for the experimental WebMCP API, videos with custom `fps`, CSS-styled action decorations and VP9 encoding, `testProject.default`, `test.describe.configure({ lock })`, **`--shuffle`** (random test order with a printed, replayable seed). `1.64.1` is too young
- Adopted: CI (`e2e.yml`) runs `npx playwright test --shuffle`, so a spec that silently depends on another's state fails; a failing order is replayed with `--shuffle <seed>`. A shuffled local run against the minikube stack passed (13/13, seed 46221466)
- Browsers updated with it (`npx playwright install chromium`; Chrome Headless Shell 156)

### @types/node `^24.0.0` (24.19.2) `→ ^26.6.4` (major)
- Matches the Node 26 the tests run on (`frontend/.nvmrc` in CI); `npm run typecheck` passes

Checked and left unchanged: `typescript` (6.0 API alias) `6.0.2` — latest. `npm audit`: 0 vulnerabilities.
