# Dependency Updates

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
