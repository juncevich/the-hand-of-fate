#!/usr/bin/env bash
# Writes the e2e variant of the gateway config to .e2e-build/nginx.conf: the auth rate limit of
# infra/nginx/nginx.conf (5 r/min per IP) and Keycloak's (30 posts/min) are raised, because the browser tests sign up and sign
# in many times from one address — every full page load also refreshes the session. The rest of
# the gateway (routing, headers, CSP) stays exactly as in production. Run by k8s-start.sh and
# infra/k8s/check.sh before the overlay is built.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
mkdir -p "$ROOT/.e2e-build"
sed -e 's/zone=auth:10m rate=5r\/m/zone=auth:10m rate=600r\/m/' \
    -e 's/limit_req zone=auth burst=10 nodelay/limit_req zone=auth burst=200 nodelay/' \
    -e 's/zone=keycloak:10m rate=30r\/m/zone=keycloak:10m rate=600r\/m/' \
    -e 's/limit_req zone=keycloak burst=30 nodelay/limit_req zone=keycloak burst=200 nodelay/' \
    "$ROOT/infra/nginx/nginx.conf" >"$ROOT/.e2e-build/nginx.conf"
[[ "$(grep -c 'rate=600r/m' "$ROOT/.e2e-build/nginx.conf")" == 2 ]] \
  || { echo "prepare.sh: the auth limits in infra/nginx/nginx.conf changed; update the sed patterns" >&2; exit 1; }
