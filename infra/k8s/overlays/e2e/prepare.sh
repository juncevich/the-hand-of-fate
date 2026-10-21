#!/usr/bin/env bash
# Writes the e2e variant of the gateway config to .e2e-build/nginx.conf:
#  - /mock-oidc/ for the mock OIDC provider of the social sign-in test (mock-oidc.nginx);
#  - the per-IP limit of infra/nginx/nginx.conf on Keycloak's sign-in forms (30 posts/min) is
#    raised, because the browser tests and the simulation sign up and in many times from one address.
# The rest of the gateway (routing, headers, CSP) stays exactly as in production. Run by
# k8s-start.sh and infra/k8s/check.sh before the overlay is built.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
mkdir -p "$ROOT/.e2e-build"
sed -e 's/zone=keycloak:10m rate=30r\/m/zone=keycloak:10m rate=600r\/m/' \
    -e 's/limit_req zone=keycloak burst=30 nodelay/limit_req zone=keycloak burst=200 nodelay/' \
    "$ROOT/infra/nginx/nginx.conf" \
  | awk -v snippet="$ROOT/infra/k8s/overlays/e2e/mock-oidc.nginx" \
      '/# Keycloak \(docs\/adr\/0001\) on the app/ { while ((getline line < snippet) > 0) print line } { print }' \
  >"$ROOT/.e2e-build/nginx.conf"
grep -q 'location /mock-oidc/' "$ROOT/.e2e-build/nginx.conf" \
  || { echo "prepare.sh: the Keycloak location comment in infra/nginx/nginx.conf changed; update the awk pattern" >&2; exit 1; }
[[ "$(grep -c 'rate=600r/m' "$ROOT/.e2e-build/nginx.conf")" == 1 ]] \
  || { echo "prepare.sh: the Keycloak limit in infra/nginx/nginx.conf changed; update the sed patterns" >&2; exit 1; }
