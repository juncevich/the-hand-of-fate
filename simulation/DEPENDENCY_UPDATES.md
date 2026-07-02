# Dependency Updates

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
