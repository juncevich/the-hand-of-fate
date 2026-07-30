---
name: update-dependencies
description: Use when asked to update/upgrade/audit dependencies in any of backend, frontend, bot, perf, or simulation — checks each explicit version against the real latest release, applies safe bumps, verifies with tests, and records the change in that project's DEPENDENCY_UPDATES.md.
---

# update-dependencies

Updates dependency versions in one or more of this monorepo's projects and records the change, per the user's standing requirement to keep a changelog of dependency bumps.

## Scope — which project(s)

| Project | Manifest | Version source of truth |
|---|---|---|
| `backend` | `backend/build.gradle.kts` (+ Gradle wrapper) | Maven Central `maven-metadata.xml` `<release>` field for each artifact; Gradle Plugin Portal for plugins — **not** `search.maven.org`'s Solr index, which has been observed to under-report several packages already at their true latest |
| `frontend` | `frontend/package.json` | npm registry (`npm view <pkg> versions` / `npm outdated`) |
| `bot` | `bot/go.mod` | `go list -m -u all` / Go proxy |
| `perf` | `perf/build.gradle.kts` | same as backend (Gradle/Maven) |
| `simulation` | `simulation/go.mod` | same as bot (Go proxy) |

Only touch the project(s) the user asked about, unless they say "all" / "everywhere."

## Steps

1. **Enumerate current explicit versions** in the manifest(s) in scope.
2. **Check each against its real latest stable release** — for Gradle/Maven artifacts, use the metadata XML `<release>` field, not a secondary index that can lag. For Go modules, `go list -m -u all`. For npm, `npm outdated` / registry `dist-tags.latest`.
3. **Decide scope of bump**: default to patch/minor unless the user explicitly said majors are in scope ("per user request, majors included" is the established phrasing in this repo's changelog). Skip pre-release/RC/beta versions and non-semver stray publishes (e.g. bare git-hash tags on Maven Central) even if they sort higher — note them as intentionally skipped.
4. **Apply the version bumps** in the manifest.
5. **Verify**:
   ```bash
   # backend / perf
   ./gradlew compileKotlin compileTestKotlin
   ./gradlew test
   ./gradlew detekt
   ./gradlew spotlessCheck

   # frontend
   npm install
   npm run lint
   npm test
   npm run build

   # bot / simulation
   go build ./...
   go test ./...
   ```
6. **Record in `<project>/DEPENDENCY_UPDATES.md`** — this is a hard requirement in this repo, not optional. Add a new dated section (`## YYYY-MM-DD`) at the top, following the existing style in that file:
   - One subsection per updated package: `### <package> (\`<gradle-property-if-any>\`) \`<old> → <new>\``
   - Bullet(s) explaining the change / how it was verified (metadata source, changelog if checked).
   - **If a package jumped through intermediate versions** (e.g. `1.2.0 → 1.2.3`), the entry must cover each intermediate version's changes (`1.2.1`, `1.2.2`, `1.2.3`), not just a diff between the endpoints.
   - A closing note listing what was checked but left unchanged (already latest, or intentionally skipped pre-release/RC), matching the verbose style already in the file — this is useful audit trail, keep it.
   - State how the bump was verified (which commands passed).
7. **If multiple projects were updated in the same pass**, update `DEPENDENCY_UPDATES.md` in every affected project, not just one.
8. **Sync version numbers in documentation.** Version tables/prose in `CLAUDE.md`, the root `README.md`, and `<project>/README.md` drift out of sync with the manifests otherwise (observed repeatedly — these files quote explicit versions like "Kotlin 2.3.21" or "React 19.2.5" that silently go stale). For every package whose version was bumped, grep these files for the old version string and update it to match the new manifest value:
   - `CLAUDE.md` — the `## Monorepo Structure` block (e.g. `backend/ Kotlin X + Spring Boot Y`)
   - `README.md` — `## Monorepo Structure` and `## Tech Stack` table
   - `<project>/README.md` — its own `## Tech Stack` (or equivalent) table
   Don't touch `DEPENDENCY_UPDATES.md` entries here (step 6 already covers those) — this step is only about the *current-state* version tables elsewhere, not the changelog.

## Gotchas

- Don't silently skip majors without saying so — always state whether majors were in scope for this pass.
- Don't invent a changelog entry for a package that didn't actually change.
- Cross-check artifacts that publish in lockstep (e.g. `io.grpc:*` submodules, `protobuf-java`/`protobuf-kotlin`) move together — verify the family, not just one artifact.
