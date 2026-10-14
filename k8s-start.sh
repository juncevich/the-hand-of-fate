#!/usr/bin/env bash
# Full stack in a local minikube cluster (profile `hand-of-fate`). Idempotent: creates or
# resumes the cluster, builds the images into minikube's Docker daemon, applies
# infra/k8s/overlays/local and waits for the rollout.
#
#   ./k8s-start.sh                       build everything and deploy
#   ./k8s-start.sh --skip-build          redeploy manifests only (reuse built images)
#   ./k8s-start.sh --only backend,bot    rebuild + restart just these (backend|frontend|bot)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OVERLAY="$ROOT/infra/k8s/overlays/local"
PROFILE=hand-of-fate
NAMESPACE=hand-of-fate
APPS=(backend frontend bot)
# nodePorts from external-services.yaml: gateway, postgres, mailpit UI, rustfs console
HOST_PORTS=(30080 30432 30025 30901)

MINIKUBE_CPUS="${MINIKUBE_CPUS:-4}"
MINIKUBE_MEMORY="${MINIKUBE_MEMORY:-6g}"

BUILD=true
ONLY=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) BUILD=false ;;
    --only)       ONLY="${2:?--only needs a list, e.g. backend,bot}"; shift ;;
    -h|--help)    sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)            echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done
if [[ -n "$ONLY" ]]; then
  IFS=',' read -r -a APPS <<<"$ONLY"
  for app in "${APPS[@]}"; do
    [[ " backend frontend bot " == *" $app "* ]] || { echo "Unknown app: $app" >&2; exit 2; }
  done
fi

kc() { kubectl --context "$PROFILE" --namespace "$NAMESPACE" "$@"; }

# ── Prerequisites ───────────────────────────────────────────────────────────
for tool in docker minikube kubectl; do
  command -v "$tool" &>/dev/null || { echo "ERROR: '$tool' not found (brew install $tool)" >&2; exit 1; }
done
docker info &>/dev/null || { echo "ERROR: Docker is not running" >&2; exit 1; }

# ── Secrets from .env ───────────────────────────────────────────────────────
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
BOT_TOKEN="${BOT_TOKEN:-}"
[[ "$BOT_TOKEN" == "your_telegram_bot_token_here" ]] && BOT_TOKEN=""
umask 077
cat >"$OVERLAY/.secrets.env" <<EOF
GRPC_SHARED_SECRET=${GRPC_SHARED_SECRET:-dev-grpc-shared-secret-for-testing-only}
BOT_TOKEN=${BOT_TOKEN}
EOF

# ── Cluster ─────────────────────────────────────────────────────────────────
host_state="$(minikube status -p "$PROFILE" --format '{{.Host}}' 2>/dev/null || true)"
if [[ "$host_state" == "Running" ]]; then
  echo "==> minikube '$PROFILE' is already running."
elif [[ -n "$host_state" && "$host_state" != *"not found"* && "$host_state" != "Nonexistent" ]]; then
  echo "==> Resuming minikube '$PROFILE'..."
  minikube start -p "$PROFILE"
else
  echo "==> Creating minikube '$PROFILE' (cpus=$MINIKUBE_CPUS, memory=$MINIKUBE_MEMORY)..."
  port_args=()
  for p in "${HOST_PORTS[@]}"; do port_args+=(--ports "127.0.0.1:$p:$p"); done
  minikube start -p "$PROFILE" --driver=docker \
    --cpus="$MINIKUBE_CPUS" --memory="$MINIKUBE_MEMORY" "${port_args[@]}"
fi

# ── Images ──────────────────────────────────────────────────────────────────
# Built by the host Docker (sharing its layer cache with docker-compose) and copied into
# the node with `minikube image load` — independent of the node's container runtime and of
# the host's buildx driver (`--load` puts the result in the local image store either way).
if "$BUILD"; then
  for app in "${APPS[@]}"; do
    echo "==> Building fate-$app:dev..."
    case "$app" in
      backend)  docker buildx build --load -t fate-backend:dev  -f "$ROOT/backend/Dockerfile" "$ROOT" ;;
      frontend) docker buildx build --load -t fate-frontend:dev "$ROOT/frontend" ;;
      bot)      docker buildx build --load -t fate-bot:dev      -f "$ROOT/bot/Dockerfile" "$ROOT" ;;
    esac
    echo "==> Loading fate-$app:dev into minikube..."
    minikube -p "$PROFILE" image load --overwrite "fate-$app:dev"
  done
fi

# ── Manifests ───────────────────────────────────────────────────────────────
# Deployments that already run old images; freshly created ones start on the new build anyway.
existing=" $(kc get deployments -o name 2>/dev/null | tr '\n' ' ') "

echo "==> Applying infra/k8s/overlays/local..."
kubectl kustomize --load-restrictor LoadRestrictionsNone "$OVERLAY" \
  | kubectl --context "$PROFILE" apply -f -

# The image tag stays `dev`, so a rebuild needs an explicit restart to be picked up.
if "$BUILD"; then
  for app in "${APPS[@]}"; do
    if [[ "$existing" == *" deployment.apps/$app "* ]]; then
      kc rollout restart "deployment/$app" >/dev/null
    fi
  done
fi

if [[ -z "$BOT_TOKEN" ]]; then
  echo "==> BOT_TOKEN not set in .env — scaling the bot to 0."
  kc scale deployment/bot --replicas=0 >/dev/null
fi

echo "==> Waiting for rollout..."
kc rollout status statefulset/postgres --timeout=180s
for d in rustfs mailpit backend frontend gateway; do
  kc rollout status "deployment/$d" --timeout=300s
done
# After a resume the rollout status above is stale (pods are still restarting), so also
# wait until a request makes it through the gateway to the backend (401 = no token).
for _ in $(seq 1 90); do
  code="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:30080/api/v1/votes || true)"
  [[ "$code" == "401" ]] && break
  sleep 2
done
[[ "$code" == "401" ]] || { echo "ERROR: gateway → backend not answering (last HTTP $code)" >&2; exit 1; }

cat <<EOF

  App       → http://localhost:30080      (admin@admin.com / admin)
  Mailpit   → http://localhost:30025
  RustFS    → http://localhost:30901/rustfs/console/  (rustfsadmin / rustfsadmin)
  Postgres  → localhost:30432             (fate / fate, db fate)

  Swagger:  kubectl --context $PROFILE -n $NAMESPACE port-forward svc/backend 18080:8080
            → http://localhost:18080/swagger-ui.html
  Logs:     kubectl --context $PROFILE -n $NAMESPACE logs -f deploy/backend
  Status:   make k8s-status        Stop: ./k8s-stop.sh

EOF
