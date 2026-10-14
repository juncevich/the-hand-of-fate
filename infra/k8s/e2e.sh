#!/usr/bin/env bash
# End-to-end checks against the running minikube stack — run by CI (.github/workflows/e2e.yml)
# and `make k8s-e2e`. Deploy first with the e2e overlay (raised auth rate limit):
#   K8S_OVERLAY=e2e ./k8s-start.sh
#
#   1. gateway: the SPA is served and a login goes through nginx to the backend
#   2. simulation: every scenario (session, simple, options, fair) against the backend
#   3. notifications: the scenarios' invitations/draw results reach Mailpit over SMTP
#   4. monitoring (if deployed): backend metrics in Mimir, pod logs in Loki, traces in Tempo
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROFILE=hand-of-fate
NAMESPACE=hand-of-fate
APP=http://localhost:30080
MAILPIT=http://localhost:30025
GRAFANA=http://admin:admin@localhost:30300
BACKEND_PORT=18081   # port-forward; 18080 is taken by Tilt's forward

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
  echo "WARNING: not the e2e overlay — the auth rate limit (10/min) will likely fail the" >&2
  echo "         simulation. Deploy with: K8S_OVERLAY=e2e ./k8s-start.sh --skip-build" >&2
fi

# ── 1. Through the gateway ──────────────────────────────────────────────────
step "Gateway"
curl -fsS "$APP/" | grep -q '<div id="root">' || fail "SPA not served on $APP"
token="$(curl -fsS -X POST "$APP/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"admin@admin.com","password":"admin"}' | json 'd["accessToken"]')"
[[ -n "$token" ]] || fail "login through the gateway returned no access token"
code="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $token" "$APP/api/v1/votes")"
[[ "$code" == 200 ]] || fail "authorized GET /api/v1/votes via gateway: HTTP $code"
echo "    SPA, login and an authorized call via nginx: ok"

# ── 2. Simulation ───────────────────────────────────────────────────────────
# Straight to the backend: the gateway's own auth limit (nginx, 5 r/min) is part of the
# shared docker-compose config and isn't relaxed for tests.
step "Simulation (all scenarios)"
mail_before="$(curl -fsS "$MAILPIT/api/v1/messages?limit=1" | json 'd["total"]')"
sim_log="$(mktemp)"
kc port-forward svc/backend "$BACKEND_PORT:8080" >/dev/null 2>&1 &
pf=$!
trap 'kill "$pf" 2>/dev/null || true; rm -f "$sim_log"' EXIT
eventually 12 curl -s -o /dev/null "http://localhost:$BACKEND_PORT/api/v1/votes" \
  || fail "port-forward to the backend didn't come up"
(cd "$ROOT/simulation" && go run ./cmd/simulate -url "http://localhost:$BACKEND_PORT") \
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
  eventually 24 has_metrics || fail "no backend metrics in Mimir"
  eventually 24 has_logs || fail "no backend logs in Loki"
  eventually 24 has_traces || fail "no backend traces in Tempo"
  echo "    backend metrics in Mimir, logs in Loki, traces in Tempo: ok"
fi

printf '\nAll end-to-end checks passed.\n'
