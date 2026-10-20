#!/usr/bin/env bash
# Writes the e2e variant of the gateway config to .e2e-build/nginx.conf:
#  - /mock-oidc/ for the mock OIDC provider of the social sign-in test (mock-oidc.nginx);
#  - the per-IP limits of infra/nginx/nginx.conf — the backend's auth endpoints (5 r/min) and
#    Keycloak's sign-in forms (30 posts/min) — are raised, because the browser tests and the
#    simulation sign up and in many times from one address.
# The rest of the gateway (routing, headers, CSP) stays exactly as in production. Run by
# k8s-start.sh and infra/k8s/check.sh before the overlay is built.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
mkdir -p "$ROOT/.e2e-build"
sed -e 's/zone=auth:10m rate=5r\/m/zone=auth:10m rate=600r\/m/' \
    -e 's/limit_req zone=auth burst=10 nodelay/limit_req zone=auth burst=200 nodelay/' \
    -e 's/zone=keycloak:10m rate=30r\/m/zone=keycloak:10m rate=600r\/m/' \
    -e 's/limit_req zone=keycloak burst=30 nodelay/limit_req zone=keycloak burst=200 nodelay/' \
    "$ROOT/infra/nginx/nginx.conf" \
  | awk -v snippet="$ROOT/infra/k8s/overlays/e2e/mock-oidc.nginx" \
      '/# Keycloak \(docs\/adr\/0001\) on the app/ { while ((getline line < snippet) > 0) print line } { print }' \
  >"$ROOT/.e2e-build/nginx.conf"
grep -q 'location /mock-oidc/' "$ROOT/.e2e-build/nginx.conf" \
  || { echo "prepare.sh: the Keycloak location comment in infra/nginx/nginx.conf changed; update the awk pattern" >&2; exit 1; }
[[ "$(grep -c 'rate=600r/m' "$ROOT/.e2e-build/nginx.conf")" == 2 ]] \
  || { echo "prepare.sh: the auth limits in infra/nginx/nginx.conf changed; update the sed patterns" >&2; exit 1; }
