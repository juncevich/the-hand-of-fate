#!/usr/bin/env bash
# Stop the local minikube cluster started by k8s-start.sh.
#
#   ./k8s-stop.sh            stop the cluster; Postgres/RustFS data and built images are kept
#   ./k8s-stop.sh --delete   delete the cluster entirely (all data, images, PVCs)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROFILE=hand-of-fate

case "${1:-}" in
  "")
    echo "==> Stopping minikube '$PROFILE' (data is kept)..."
    minikube stop -p "$PROFILE"
    ;;
  --delete)
    echo "==> Deleting minikube '$PROFILE' with all its data..."
    minikube delete -p "$PROFILE"
    rm -f "$ROOT/infra/k8s/overlays/local/.secrets.env"
    ;;
  -h|--help)
    sed -n '2,5p' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *)
    echo "Unknown argument: $1" >&2
    exit 2
    ;;
esac

echo "==> Done."
