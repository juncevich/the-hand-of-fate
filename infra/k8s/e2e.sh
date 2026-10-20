#!/usr/bin/env bash
# End-to-end checks against the running minikube stack — run by CI (.github/workflows/e2e.yml)
# and `make k8s-e2e`. Deploy first with the e2e overlay (raised auth rate limit):
#   K8S_OVERLAY=e2e ./k8s-start.sh
#
#   1. gateway: the SPA is served, Keycloak issues a token on /auth/, the backend accepts it on /api/
#   2. simulation: every scenario (session, simple, options, fair) through the gateway — users sign
#      up on Keycloak's pages and confirm their email from Mailpit
#   3. notifications: the scenarios' invitations/draw results reach Mailpit over SMTP
#   4. monitoring (if deployed): backend metrics in Mimir, pod logs in Loki, traces in Tempo;
#      Keycloak's sign-in metrics and traces
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROFILE=hand-of-fate
NAMESPACE=hand-of-fate
APP=http://localhost:30080
MAILPIT=http://localhost:30025
GRAFANA=http://admin:admin@localhost:30300

kc() { kubectl --context "$PROFILE" --namespace "$NAMESPACE" "$@"; }
step() { printf '\n==> %s\n' "$*"; }
fail() { echo "FAIL: $*" >&2; exit 1; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }

# Retries a command until it succeeds (metrics/logs arrive asynchronously).
eventually() {
  local tries="$1"; shift
  for _ in $(seq 1 "$tries"); do
    "$@" && return 0
    sleep 5
  done
  return 1
}

if ! kc get configmap -o yaml | grep -q 'APP_RATELIMIT_CAPACITY'; then
  echo "WARNING: not the e2e overlay — the gateway's per-IP limits on sign-in forms may fail the" >&2
  echo "         browser tests. Deploy with: K8S_OVERLAY=e2e ./k8s-start.sh --skip-build" >&2
fi

# ── 1. Through the gateway ──────────────────────────────────────────────────
step "Gateway"
curl -fsS "$APP/" | grep -q '<div id="root">' || fail "SPA not served on $APP"
# The demo user through `fate-test`, the dev/e2e realm's direct-grant client.
token="$(curl -fsS "$APP/auth/realms/fate/protocol/openid-connect/token" -d grant_type=password \
  -d client_id=fate-test -d username=admin@admin.com -d password=admin123 | json 'd["access_token"]')"
[[ -n "$token" ]] || fail "Keycloak through the gateway returned no access token"
code="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $token" "$APP/api/v1/votes")"
[[ "$code" == 200 ]] || fail "authorized GET /api/v1/votes via gateway: HTTP $code"
echo "    SPA, a Keycloak token and an authorized call via nginx: ok"

# ── 2. Simulation ───────────────────────────────────────────────────────────
# Through the gateway, like a browser: Keycloak's pages on /auth/, the backend on /api/.
step "Simulation (all scenarios)"
mail_before="$(curl -fsS "$MAILPIT/api/v1/messages?limit=1" | json 'd["total"]')"
sim_log="$(mktemp)"
trap 'rm -f "$sim_log"' EXIT
(cd "$ROOT/simulation" && go run ./cmd/simulate -url "$APP" -app "$APP" -mailpit "$MAILPIT") \
  2>"$sim_log" || { tail -30 "$sim_log" >&2; fail "simulation"; }

# ── 3. Notifications ────────────────────────────────────────────────────────
step "Notifications (outbox → SMTP → Mailpit)"
mail_arrived() {
  local now
  now="$(curl -fsS "$MAILPIT/api/v1/messages?limit=1" | json 'd["total"]')"
  ((now > mail_before))
}
eventually 24 mail_arrived || fail "no new mail in Mailpit after the scenarios"
echo "    $(($(curl -fsS "$MAILPIT/api/v1/messages?limit=1" | json 'd["total"]') - mail_before)) new message(s)"

# ── 4. Monitoring ───────────────────────────────────────────────────────────
if kc get deployment/grafana &>/dev/null; then
  step "Monitoring (Mimir metrics, Loki logs, Tempo traces)"
  uid() { curl -fsS "$GRAFANA/api/datasources/name/$1" | json 'd["uid"]'; }
  mimir="$GRAFANA/api/datasources/proxy/uid/$(uid Mimir)"
  loki="$GRAFANA/api/datasources/proxy/uid/$(uid Loki)"
  tempo="$GRAFANA/api/datasources/proxy/uid/$(uid Tempo)"
  has_metrics() {
    curl -fsS -G "$mimir/api/v1/query" --data-urlencode 'query=count(hikaricp_connections)' \
      | json 'len(d["data"]["result"])' | grep -qv '^0$'
  }
  has_logs() {
    curl -fsS -G "$loki/loki/api/v1/query_range" --data-urlencode 'query={service_name="backend"}' \
      --data-urlencode limit=1 | json 'len(d["data"]["result"])' | grep -qv '^0$'
  }
  has_traces() {
    curl -fsS -G "$tempo/api/search" --data-urlencode 'tags=service.name=hand-of-fate-backend' \
      --data-urlencode limit=1 | json 'len(d.get("traces") or ())' | grep -qv '^0$'
  }
  has_keycloak_metrics() {   # scraped by the collector; the simulation signed users up and in
    curl -fsS -G "$mimir/api/v1/query" \
      --data-urlencode 'query=sum(keycloak_user_events_total{job="keycloak",realm="fate",event="login"})' \
      | json 'len(d["data"]["result"])' | grep -qv '^0$'
  }
  has_keycloak_traces() {
    curl -fsS -G "$tempo/api/search" --data-urlencode 'tags=service.name=keycloak' \
      --data-urlencode limit=1 | json 'len(d.get("traces") or ())' | grep -qv '^0$'
  }
  eventually 24 has_metrics || fail "no backend metrics in Mimir"
  eventually 24 has_logs || fail "no backend logs in Loki"
  eventually 24 has_traces || fail "no backend traces in Tempo"
  eventually 24 has_keycloak_metrics || fail "no Keycloak sign-in metrics in Mimir"
  eventually 24 has_keycloak_traces || fail "no Keycloak traces in Tempo"
  echo "    backend metrics in Mimir, logs in Loki, traces in Tempo; Keycloak metrics and traces: ok"
fi

printf '\nAll end-to-end checks passed.\n'
