# Dependency Updates

## 2026-11-09 — recovered-stack validation

- Docker 29.8.2 and minikube recovered; all application/infrastructure pods are ready and the realm-config Job completed. Gateway responds with HTTP 200. Confirmed Keycloak 26.8.2 and Grafana 13.3.0.
- First shuffled Playwright 1.65.0 run: 12 passed / 1 failed, seed 650070935. The email-preferences test reloaded immediately after toggling, before its save request was sent (trace contained GETs but no PUT).
- Fixed that test to await a successful PUT /api/v1/users/me/email-preferences together with each toggle before reloading, then retain the post-reload persistence assertions. No sleeps or application changes were needed.
- Passed npm run typecheck and the complete repeat run: `npx playwright test --shuffle 650070935`, 13/13 tests passed in 2.2 minutes with one worker and no retries. This closes the browser-validation gap recorded on November 6–7.

## 2026-11-06 — stage 5: Playwright

### @playwright/test `1.64.1 → 1.65.0`

- [1.65.0 release notes](https://github.com/microsoft/playwright/releases/tag/v1.65.0): adds Bun support, a failures reporter, failure-only report options and an exclusive test lock. No intermediate stable releases were published between 1.64.1 and 1.65.0.
- Compatibility changes include TypeScript class-field semantics, waiting for non-inert elements before actions, stricter cookie matching and accessible-name calculation. Existing tests need no static configuration changes; Node remains 26.13.0 and the runner stays exactly pinned.
- playwright and playwright-core move together in package-lock.json. Installed Chromium/Headless Shell 158.0.8127.18 (Playwright build 1254).
- Passed npm install, npm run typecheck and npx playwright test --list (13 tests across 4 files). Full browser execution could not start: deployment of the local e2e stack stalled before PostgreSQL readiness, and the minikube API stopped responding (TLS handshake timeouts). On 2026-11-07 Docker/minikube status checks still timed out and gateway returned HTTP 000. No browser-suite pass is claimed. Re-run `npx playwright test --shuffle` against the recovered, updated e2e stack.
- @types/node 26.6.6 and the TypeScript 6 API alias remain unchanged; no prereleases introduced. Majors were included in the audit, and this stage selects the current stable 1.x runner.

## 2026-11-02 — all dependencies, majors in scope

Latest stable releases checked against the upstream registries on 2026-11-02; per user request, majors included. No minimum release-age restriction in this pass. Pre-releases are excluded; npm dist-tags.latest selects stable package releases. Each release-history link below identifies the changes for that intermediate version.

### @playwright/test `1.64.0 → 1.64.1`

- Browser test runner updated; TypeScript and discovery of all 13 browser tests pass.
- Intermediate release metadata/source references: [1.64.1](https://registry.npmjs.org/@playwright%2ftest/1.64.1).

### @types/node `26.6.4 → 26.6.6`

- Node 26 declarations; compiler and configuration imports still type-check.
- Intermediate release metadata/source references: [26.6.5](https://registry.npmjs.org/@types%2fnode/26.6.5) → [26.6.6](https://registry.npmjs.org/@types%2fnode/26.6.6).

### Checked, left unchanged

- TypeScript API alias `npm:@typescript/typescript6@^6.0.2`: latest stable.

Verified: `npm run typecheck`, `npx playwright test --list` (13 tests); install audit: 0 vulnerabilities. The browser suite itself was not run against a deployed stack.

## 2026-10-21 — all dependencies, majors in scope

Checked against the npm registry; stable releases at least two weeks old, majors in scope.

### @playwright/test `1.63.0 → 1.64.0` (pinned exactly, as before)
- 1.64: `page.webmcp` for the experimental WebMCP API, videos with custom `fps`, CSS-styled action decorations and VP9 encoding, `testProject.default`, `test.describe.configure({ lock })`, **`--shuffle`** (random test order with a printed, replayable seed). `1.64.1` is too young
- Adopted: CI (`e2e.yml`) runs `npx playwright test --shuffle`, so a spec that silently depends on another's state fails; a failing order is replayed with `--shuffle <seed>`. A shuffled local run against the minikube stack passed (13/13, seed 46221466)
- Browsers updated with it (`npx playwright install chromium`; Chrome Headless Shell 156)

### @types/node `^24.0.0` (24.19.2) `→ ^26.6.4` (major)
- Matches the Node 26 the tests run on (`frontend/.nvmrc` in CI); `npm run typecheck` passes

Checked and left unchanged: `typescript` (6.0 API alias) `6.0.2` — latest. `npm audit`: 0 vulnerabilities.
