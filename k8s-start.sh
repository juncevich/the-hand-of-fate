#!/usr/bin/env bash
# Full stack in a local minikube cluster (profile `hand-of-fate`). Idempotent: creates or
# resumes the cluster, builds the images, applies infra/k8s/overlays/local and waits for
# the rollout. For the hot-reload loop use `make k8s-dev` (Tilt) instead.
#
#   ./k8s-start.sh                       build everything and deploy
#   ./k8s-start.sh --skip-build          redeploy manifests only (reuse built images)
#   ./k8s-start.sh --only backend,bot    rebuild + restart just these (backend|frontend|bot|keycloak)
#   ./k8s-start.sh --cluster-only        only create/resume the cluster (used by make k8s-dev)
#   K8S_OVERLAY=e2e ./k8s-start.sh       deploy another overlay built on `local` (CI uses e2e)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_OVERLAY="$ROOT/infra/k8s/overlays/local"   # owns .secrets.env for every overlay
OVERLAY_NAME="${K8S_OVERLAY:-local}"
OVERLAY="$ROOT/infra/k8s/overlays/$OVERLAY_NAME"
[[ -f "$OVERLAY/kustomization.yaml" ]] || { echo "Unknown overlay: $OVERLAY_NAME" >&2; exit 2; }
PROFILE=hand-of-fate
NAMESPACE=hand-of-fate
APPS=(backend frontend bot keycloak)
# nodePorts from external-services.yaml: gateway, postgres, mailpit UI, rustfs console
HOST_PORTS=(30080 30432 30025 30901 30300)   # + grafana

MINIKUBE_CPUS="${MINIKUBE_CPUS:-4}"
MINIKUBE_MEMORY="${MINIKUBE_MEMORY:-8g}"   # Keycloak adds ~1 GiB

BUILD=true
ONLY=""
CLUSTER_ONLY=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)   BUILD=false ;;
    --only)         ONLY="${2:?--only needs a list, e.g. backend,bot}"; shift ;;
    --cluster-only) CLUSTER_ONLY=true ;;
    -h|--help)      sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)            echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done
if [[ -n "$ONLY" ]]; then
  IFS=',' read -r -a APPS <<<"$ONLY"
  for app in "${APPS[@]}"; do
    [[ " backend frontend bot keycloak " == *" $app "* ]] || { echo "Unknown app: $app" >&2; exit 2; }
  done
fi

kc() { kubectl --context "$PROFILE" --namespace "$NAMESPACE" "$@"; }

# ── Prerequisites ───────────────────────────────────────────────────────────
for tool in docker minikube kubectl; do
  command -v "$tool" &>/dev/null || { echo "ERROR: '$tool' not found (brew install $tool)" >&2; exit 1; }
done
docker info &>/dev/null || { echo "ERROR: Docker is not running" >&2; exit 1; }

# ── Secrets from .env ───────────────────────────────────────────────────────
"$LOCAL_OVERLAY/write-secrets.sh"
BOT_TOKEN="$(sed -n 's/^BOT_TOKEN=//p' "$LOCAL_OVERLAY/.secrets.env")"
# Without a token the bot is scaled to 0 below, so don't spend a build on it (unless asked).
if [[ -z "$BOT_TOKEN" && -z "$ONLY" ]]; then
  APPS=(backend frontend keycloak)
fi

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
  # Docker runtime: Tilt builds images straight into the node's Docker daemon (no push/load).
  minikube start -p "$PROFILE" --driver=docker --container-runtime=docker \
    --cpus="$MINIKUBE_CPUS" --memory="$MINIKUBE_MEMORY" "${port_args[@]}"
fi
runtime="$(minikube profile list -o json 2>/dev/null | python3 -c '
import json, sys
for p in json.load(sys.stdin).get("valid", []):
    if p["Name"] == sys.argv[1]:
        print(p["Config"]["KubernetesConfig"]["ContainerRuntime"])' "$PROFILE" || true)"
if [[ "$runtime" != "docker" ]]; then
  echo "WARNING: '$PROFILE' doesn't run the docker runtime, which Tilt (make k8s-dev) needs." >&2
  echo "         Recreate it once: ./k8s-stop.sh --delete && ./k8s-start.sh" >&2
fi
# Port publishing is fixed when the node container is created; a newer HOST_PORTS needs a
# recreate.
for p in "${HOST_PORTS[@]}"; do
  if ! docker port "$PROFILE" "$p/tcp" &>/dev/null; then
    echo "WARNING: port $p isn't published by the '$PROFILE' node (created before it was added)." >&2
    echo "         Recreate it once: ./k8s-stop.sh --delete && ./k8s-start.sh" >&2
  fi
done
"$CLUSTER_ONLY" && exit 0

# ── Images ──────────────────────────────────────────────────────────────────
# Built by the host Docker (sharing its layer cache with docker-compose) and copied into the
# node's Docker daemon (the cluster runs the docker runtime, see above) — independent of the
# host's buildx driver (`--load` puts the result in the local image store either way).
# Not `minikube image load`: it keeps the old image when the `dev` tag already exists on the
# node, so a rebuild was silently not deployed.
if "$BUILD"; then
  for app in "${APPS[@]}"; do
    echo "==> Building fate-$app:dev..."
    case "$app" in
      backend)  docker buildx build --load -t fate-backend:dev  -f "$ROOT/backend/Dockerfile" "$ROOT" ;;
      frontend) docker buildx build --load -t fate-frontend:dev "$ROOT/frontend" ;;
      bot)      docker buildx build --load -t fate-bot:dev      -f "$ROOT/bot/Dockerfile" "$ROOT" ;;
      # Keycloak with the login theme built from the frontend (src/login/)
      keycloak) docker buildx build --load -t fate-keycloak:dev -f "$ROOT/infra/keycloak/Dockerfile" "$ROOT/frontend" ;;
    esac
    echo "==> Loading fate-$app:dev into minikube..."
    docker save "fate-$app:dev" | minikube -p "$PROFILE" ssh --native-ssh=false -- docker load >/dev/null
  done
fi

# ── Manifests ───────────────────────────────────────────────────────────────
# Deployments that already run old images; freshly created ones start on the new build anyway.
existing=" $(kc get deployments -o name 2>/dev/null | tr '\n' ' ') "

echo "==> Applying infra/k8s/overlays/$OVERLAY_NAME..."
# An overlay may generate files its kustomization reads (e.g. e2e's gateway config).
if [[ -x "$OVERLAY/prepare.sh" ]]; then "$OVERLAY/prepare.sh"; fi
# A Job's pod template is immutable; the realm config Job is rerun on every apply anyway.
kc delete job keycloak-config --ignore-not-found >/dev/null
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
kc rollout status statefulset/postgres --timeout=300s
for d in rustfs mailpit keycloak backend frontend gateway; do
  kc rollout status "deployment/$d" --timeout=300s
done
echo "==> Waiting for the realm config (keycloak-config)..."
kc wait --for=condition=complete job/keycloak-config --timeout=300s \
  || { kc logs job/keycloak-config --tail=30 >&2; exit 1; }
# Monitoring (components/observability) — skipped when the component is switched off.
if kc get daemonset/otel-collector &>/dev/null; then
  kc rollout status daemonset/otel-collector --timeout=180s
  for d in loki mimir tempo grafana; do kc rollout status "deployment/$d" --timeout=300s; done
fi
# After a resume the rollout status above is stale (pods are still restarting), so also
# wait until a request makes it through the gateway to the backend (401 = no token).
for _ in $(seq 1 90); do
  code="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:30080/api/v1/votes || true)"
  [[ "$code" == "401" ]] && break
  sleep 2
done
[[ "$code" == "401" ]] || { echo "ERROR: gateway → backend not answering (last HTTP $code)" >&2; exit 1; }

cat <<EOF

  App       → http://localhost:30080      (admin@admin.com / admin123)
  Mailpit   → http://localhost:30025
  RustFS    → http://localhost:30901/rustfs/console/  (rustfsadmin / rustfsadmin)
  Postgres  → localhost:30432             (fate / fate, db fate)
  Grafana   → http://localhost:30300      (admin / admin; Mimir metrics, Loki pod logs, Tempo traces)
  Keycloak  → http://localhost:30080/auth/admin/  (admin / admin; realm fate, demo user admin@admin.com / admin123)

  Swagger:  kubectl --context $PROFILE -n $NAMESPACE port-forward svc/backend 18080:8080
            → http://localhost:18080/swagger-ui.html
  Logs:     kubectl --context $PROFILE -n $NAMESPACE logs -f deploy/backend
  Status:   make k8s-status        Stop: ./k8s-stop.sh

EOF
