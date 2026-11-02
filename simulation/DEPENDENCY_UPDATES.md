# Dependency Updates

## 2026-11-02 — all dependencies, majors in scope

Latest stable releases checked against the upstream registries on 2026-11-02; per user request, majors included. No minimum release-age restriction in this pass. Pre-releases are excluded. YAML compatibility constraints, where applicable, are listed below; source and module-history links provide the upstream change references.

### Go directive `1.27.1 → 1.27.3`

- Current stable patch of the Go 1.27 line, aligned in both modules; [Go release history](https://go.dev/doc/devel/release#go1.27.3). Build images follow `golang:1.27-alpine`.

### github.com/getkin/kin-openapi `v0.142.0 → v0.152.0`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v0.152.0](https://pkg.go.dev/github.com/getkin/kin-openapi@v0.152.0); [module versions](https://proxy.golang.org/github.com/getkin/kin-openapi/@v/list).

### github.com/go-openapi/jsonpointer `v0.23.1 → v1.0.2`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v1.0.2](https://pkg.go.dev/github.com/go-openapi/jsonpointer@v1.0.2); [module versions](https://proxy.golang.org/github.com/go-openapi/jsonpointer/@v/list).

### github.com/oapi-codegen/oapi-codegen/v2 `v2.8.0 → v2.8.2`

- OpenAPI model generator; the generated client header now records v2.8.2, model definitions are unchanged.
- Upstream source/change history: [v2.8.2](https://pkg.go.dev/github.com/oapi-codegen/oapi-codegen/v2@v2.8.2); [module versions](https://proxy.golang.org/github.com/oapi-codegen/oapi-codegen/v2/@v/list).

### github.com/oasdiff/yaml `v0.1.1 → v0.1.3`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v0.1.3](https://pkg.go.dev/github.com/oasdiff/yaml@v0.1.3); [module versions](https://proxy.golang.org/github.com/oasdiff/yaml/@v/list).

### github.com/oasdiff/yaml3 `v0.0.14 → v0.0.16`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v0.0.16](https://pkg.go.dev/github.com/oasdiff/yaml3@v0.0.16); [module versions](https://proxy.golang.org/github.com/oasdiff/yaml3/@v/list).

### github.com/santhosh-tekuri/jsonschema/v6 `v6.0.2 → v6.0.3`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v6.0.3](https://pkg.go.dev/github.com/santhosh-tekuri/jsonschema/v6@v6.0.3); [module versions](https://proxy.golang.org/github.com/santhosh-tekuri/jsonschema/v6/@v/list).

### github.com/speakeasy-api/openapi `v1.24.0 → v1.24.1`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v1.24.1](https://pkg.go.dev/github.com/speakeasy-api/openapi@v1.24.1); [module versions](https://proxy.golang.org/github.com/speakeasy-api/openapi/@v/list).

### go.yaml.in/yaml/v3 `v3.0.5 → v3.0.6`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v3.0.6](https://pkg.go.dev/go.yaml.in/yaml/v3@v3.0.6); [module versions](https://proxy.golang.org/go.yaml.in/yaml/v3/@v/list).

### golang.org/x/mod `v0.38.0 → v0.42.0`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v0.42.0](https://pkg.go.dev/golang.org/x/mod@v0.42.0); [module versions](https://proxy.golang.org/golang.org/x/mod/@v/list).

### golang.org/x/sync `v0.22.0 → v0.24.0`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v0.24.0](https://pkg.go.dev/golang.org/x/sync@v0.24.0); [module versions](https://proxy.golang.org/golang.org/x/sync/@v/list).

### golang.org/x/text `v0.40.0 → v0.43.0`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v0.43.0](https://pkg.go.dev/golang.org/x/text@v0.43.0); [module versions](https://proxy.golang.org/golang.org/x/text/@v/list).

### golang.org/x/tools `v0.48.0 → v0.52.0`

- Updated declared module version; build, unit tests and go vet pass.
- Upstream source/change history: [v0.52.0](https://pkg.go.dev/golang.org/x/tools@v0.52.0); [module versions](https://proxy.golang.org/golang.org/x/tools/@v/list).

### Removed redundant indirect requirements

- `github.com/go-openapi/swag/jsonname`, `github.com/stretchr/testify`: `go mod tidy` no longer needs these as root requirements.

### Compatibility constraints / checked, left unchanged

- `speakeasy-api/openapi` stops at `1.24.1`: `1.25+` exposes `go.yaml.in/yaml/v3.Node`, but the latest oapi-codegen `2.8.2` still passes `gopkg.in/yaml.v3.Node`. Tested upgrade to `1.26.3` fails to compile upstream loader.go.
- Keep `speakeasy-api/jsonpath` `0.6.3` and `go-yit` `v0.0.0-20220510233725-9ba8df137936` on the same gopkg YAML node API. Newer go-yit revisions switch first to go.yaml v3 and then to v4 RC; both break their current consumers. No YAML v4 RC is retained.
- UUID `1.6.0`, zap `1.28.0`, multierr `1.11.0`, vmware YAML JSONPath `0.3.2` and gopkg YAML `3.0.1`: unchanged. The redundant swag/jsonname and testify root requirements were removed by tidy.

Verified: `go mod tidy`, `go generate ./...`, `go test ./...`, `go vet ./...`, `go build ./...`. Live user scenarios were not run.

## 2026-10-21

Re-checked every module via the Go module proxy, majors in scope (same pass as `bot`). No changes — already latest: `github.com/google/uuid` `v1.6.0`, `go.uber.org/zap` `v1.28.0`, tool `github.com/oapi-codegen/oapi-codegen/v2` `v2.8.0`. `go` directive `1.27.1` kept (1.27.2/1.27.3 younger than two weeks).

`go vet`, `go test ./...`, staticcheck pass; all four scenarios pass against the updated minikube stack (`infra/k8s/e2e.sh`).

## 2026-09-25

Re-checked every module in `go.mod` via the Go module proxy, per the user's request to check and update with majors in scope (same audit pass as `bot`). No changes — everything already at the latest stable release: `github.com/google/uuid` `v1.6.0`, `go.uber.org/zap` `v1.28.0`, `github.com/stretchr/testify` `v1.12.1`, `go.uber.org/multierr` `v1.11.0`, `go.yaml.in/yaml/v3` `v3.0.5`. `go` directive `1.27.1` confirmed still the newest stable release on go.dev.

`go build ./...` and `go test ./...` pass; `go.mod`/`go.sum` byte-identical to before this session.

## 2026-09-15

Re-checked every module in `go.mod` via the Go module proxy, per the user's request to check and update with majors in scope (same audit pass as `bot`). No new major branches for either direct dependency.

### go directive `1.25.0 → 1.27.1`
- Same rationale as the `bot` bump this pass — picks up the newest stable Go release (go.dev also lists `go1.26.8` as a patch-only alternative)

### github.com/stretchr/testify (indirect) `v1.11.1 → v1.12.1`
- One minor version behind; pulled in a new transitive `go.yaml.in/yaml/v3 v3.0.5` dependency via `go mod tidy`

Already at latest, confirmed unchanged: `github.com/google/uuid` `v1.6.0`, `go.uber.org/zap` `v1.28.0`, `go.uber.org/multierr` `v1.11.0`.

`go build ./...` and `go test ./...` pass.

## 2026-08-06

Re-checked every module in `go.mod` (direct and transitive) via `go list -u -m all`, per the user's request to check and update. No new major branches for either direct dependency. Ran `go get -u ./...` + `go mod tidy` — no changes were pulled; `go.mod`/`go.sum` byte-identical to before this session.

Already at latest, confirmed unchanged: `github.com/google/uuid` `v1.6.0`, `go.uber.org/zap` `v1.28.0`, `github.com/stretchr/testify` `v1.11.1`, `go.uber.org/multierr` `v1.11.0`, `go.yaml.in/yaml/v3` `v3.0.4`, `gopkg.in/yaml.v3` `v3.0.1`, and the other transitive test-only deps. `go build ./...` and `go test ./...` pass.

## 2026-07-30

Re-checked every module in `go.mod` (direct and transitive) via `go list -u -m all`, per explicit user request to include majors. No new major branches for either direct dependency. Ran `go get -u ./...` + `go mod tidy` — no changes were pulled; `go.mod`/`go.sum` byte-identical to before this session (this module's build graph resolves `go.yaml.in/yaml/v3` to `v3.0.4` regardless of the newer `v3.0.5` seen elsewhere in the repo, since it's a different transitive path than `bot`'s).

Already at latest, confirmed unchanged: `github.com/google/uuid` `v1.6.0`, `go.uber.org/zap` `v1.28.0`, `github.com/stretchr/testify` `v1.11.1`, `go.uber.org/multierr` `v1.11.0`, `go.yaml.in/yaml/v3` `v3.0.4`, `gopkg.in/yaml.v3` `v3.0.1`, and the other transitive test-only deps. `go build ./...` and `go test ./...` pass.

## 2026-07-10

Re-checked every module in `go.mod` (direct and transitive) via `go list -u -m all`, per explicit user request to include majors this time. No new major branches exist for either direct dependency. Ran `go get -u` on both direct deps + `go mod tidy` — no changes were pulled; `go.mod`/`go.sum` are byte-identical to before this session.

Already at latest, confirmed unchanged: `github.com/google/uuid` `v1.6.0`, `go.uber.org/zap` `v1.28.0`, `github.com/stretchr/testify` `v1.11.1`, `go.uber.org/multierr` `v1.11.0`, `go.yaml.in/yaml/v3` `v3.0.4`, `gopkg.in/yaml.v3` `v3.0.1`, and the other transitive test-only deps (`davecgh/go-spew`, `pmezard/go-difflib`, `kr/text`, `go.uber.org/goleak`). `go build ./...` and `go test ./...` pass.

## 2026-07-07

Checked every dependency in `simulation/go.mod` (direct and transitive) via
`go list -m -u all` against the Go module proxy:

| Module | Was | Latest available | Action |
|---|---|---|---|
| `github.com/google/uuid` (direct) | v1.6.0 | v1.6.0 | already latest — no change |
| `go.uber.org/zap` (direct) | v1.28.0 | v1.28.0 | already latest — no change |
| `go.uber.org/multierr` (indirect) | v1.11.0 | v1.11.0 | already latest — no change |
| `github.com/stretchr/testify` (indirect) | v1.8.1 | v1.11.1 | **bumped** — see below |
| `github.com/davecgh/go-spew` (indirect) | v1.1.1 | v1.1.1 | already latest — no change |
| `github.com/pmezard/go-difflib` (indirect) | v1.0.0 | v1.0.0 | already latest — no change |
| `github.com/kr/text` (indirect) | v0.2.0 | v0.2.0 | already latest — no change |
| `go.uber.org/goleak` (indirect) | v1.3.0 | v1.3.0 | already latest — no change |
| `go.yaml.in/yaml/v3` (indirect) | v3.0.4 | v3.0.4 | already latest — no change |
| `gopkg.in/yaml.v3` (indirect) | v3.0.1 | v3.0.1 | already latest — no change |

### `stretchr/testify` v1.8.1 → v1.11.1 (bumped, major version jump)
`testify` is not imported directly by any `.go` file in this module
(confirmed via `grep -rl testify --include="*.go" .` — no matches, and `go
mod why -m github.com/stretchr/testify` traces it to `go.uber.org/zap`'s own
test package, `go.uber.org/zap.test`). It only appears in `go.mod` as an
`// indirect` requirement, pinned there because `go.uber.org/zap`'s `go.mod`
requires `testify v1.8.1` for zap's own tests, and that version flows into
our module graph via MVS.

Ran `go get github.com/stretchr/testify@v1.11.1` followed by `go mod tidy`,
which upgraded the indirect requirement in `go.mod`/`go.sum` from v1.8.1 to
v1.11.1. `go list -m -u all` afterward reports no newer version for testify.
(`go get -u ./...` alone did not pick up this bump, since `go get -u`
without a target only upgrades modules that are relevant to packages our
module actually builds — testify here is only pulled in for zap's *tests*,
which we don't build. It had to be requested explicitly.)

One follow-on transitive dependency, `github.com/stretchr/objx` (used by
testify's `mock` subpackage, which we don't use), shows a newer v0.5.3 in
`go list -m -u all`, but it never appears in `go.sum` — it's pruned out of
the actual build list entirely since nothing in our build imports
`testify/mock`. No action needed there.

`go build ./...` and `go test ./...` pass after the bump with no code
changes required.

## 2026-07-03

Checked every dependency in `simulation/go.mod` against the Go module proxy
(`https://proxy.golang.org/<module>/@latest`):

| Module | Was | Latest available | Action |
|---|---|---|---|
| `github.com/google/uuid` | v1.6.0 | v1.6.0 | already latest — no change |
| `go.uber.org/zap` | v1.28.0 | v1.28.0 | already latest — no change |
| `go.uber.org/multierr` (indirect) | v1.11.0 | v1.11.0 | already latest — no change |

No version bumps were needed — all three dependencies were already pinned to
their latest available versions on the proxy at the time of this check.
`go get -u ./...` followed by `go mod tidy` produced no diff in `go.mod` or
`go.sum`.

Also normalized the `go` directive from `go 1.25` to `go 1.25.0` in
`simulation/go.mod`, to match the exact form used by `bot/go.mod` (`go
1.25.0`) in this repo. The installed toolchain (`go1.26.4`) is newer, but the
`go` directive was left at `1.25.0` for consistency with `bot/`, matching the
Go version documented for both modules in the root `CLAUDE.md`.

`go build ./...` and `go test ./...` pass with no changes required.

### Note on the previous (2026-06-11) entry
The prior entry in this file claimed `go.uber.org/multierr` was bumped
`v1.10.0 → v1.11.0` on 2026-06-11. As of this check, `go.mod` already showed
`v1.11.0`, and v1.11.0 remains the latest version available on the proxy — no
further action was needed for this dependency now.
