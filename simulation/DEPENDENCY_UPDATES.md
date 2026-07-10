# Dependency Updates

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
