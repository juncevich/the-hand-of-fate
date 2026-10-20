#!/usr/bin/env bash
# Static checks for the local Kubernetes setup, no cluster needed — run by CI
# (.github/workflows/k8s.yml) and `make k8s-check`. Needs Go and Docker.
#
#   1. every overlay renders (kustomize)
#   2. every rendered object matches its Kubernetes schema (kubeconform -strict)
#   3. the configs mounted into the pods are valid for the image versions the manifests pin:
#      OTel Collector (compose config alone and merged with the k8s one), Loki, Tempo, gateway
#      nginx; Grafana dashboards are valid JSON
#   4. third-party image versions match between docker-compose*.yml and the manifests
#   5. shellcheck on the k8s scripts
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

KUSTOMIZE=(go run sigs.k8s.io/kustomize/kustomize/v5@v5.8.2)
KUBECONFORM=(go run github.com/yannh/kubeconform/cmd/kubeconform@v0.8.0)
SHELLCHECK_IMAGE=koalaman/shellcheck:v0.10.0

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

step() { printf '\n==> %s\n' "$*"; }

# ── 1. Render ───────────────────────────────────────────────────────────────
step "Rendering overlays"
# Inputs normally produced by k8s-start.sh / the Tiltfile (both gitignored).
[[ -f infra/k8s/overlays/local/.secrets.env ]] \
  || printf 'GRPC_SHARED_SECRET=check\nBOT_TOKEN=\n' >infra/k8s/overlays/local/.secrets.env
[[ -f infra/k8s/overlays/local/.oauth.env ]] \
  || printf 'OAUTH_GOOGLE_CLIENT_ID=\n' >infra/k8s/overlays/local/.oauth.env
mkdir -p .tilt-build
sed '/Content-Security-Policy/d' infra/nginx/nginx.conf >.tilt-build/nginx.conf
infra/k8s/overlays/e2e/prepare.sh
for overlay in local tilt e2e; do
  "${KUSTOMIZE[@]}" build --load-restrictor LoadRestrictionsNone "infra/k8s/overlays/$overlay" \
    >"$OUT/$overlay.yaml"
  echo "    $overlay: $(grep -c '^kind:' "$OUT/$overlay.yaml") objects"
done

# ── 2. Schemas ──────────────────────────────────────────────────────────────
step "Validating against Kubernetes schemas"
"${KUBECONFORM[@]}" -strict -summary "$OUT/local.yaml" "$OUT/tilt.yaml" "$OUT/e2e.yaml"

# ── 3. Mounted configs, with the images the manifests actually run ──────────
image_of() { grep -m1 -oE "image: $1:[^[:space:]\"]+" "$OUT/local.yaml" | cut -d' ' -f2; }

step "OTel Collector config ($(image_of otel/opentelemetry-collector-contrib))"
collector="$(image_of otel/opentelemetry-collector-contrib)"
mon="$ROOT/infra/monitoring"
obs="$ROOT/infra/k8s/components/observability"
docker run --rm -v "$mon/otel-collector.yaml:/c/config.yaml:ro" "$collector" \
  validate --config=/c/config.yaml
docker run --rm -v "$mon/otel-collector.yaml:/c/config.yaml:ro" \
  -v "$obs/otel-collector-k8s.yaml:/c/k8s.yaml:ro" "$collector" \
  validate --config=/c/config.yaml --config=/c/k8s.yaml
echo "    valid (compose, k8s merge)"

step "Loki config ($(image_of grafana/loki))"
docker run --rm -v "$mon/loki.yaml:/etc/loki/loki.yaml:ro" "$(image_of grafana/loki)" \
  -config.file=/etc/loki/loki.yaml -verify-config

step "Tempo config ($(image_of grafana/tempo))"
docker run --rm -v "$mon/tempo.yaml:/etc/tempo/tempo.yaml:ro" "$(image_of grafana/tempo)" \
  -config.file=/etc/tempo/tempo.yaml -config.verify=true
echo "    valid"

step "Grafana dashboards"
for dashboard in "$mon"/grafana/provisioning/dashboards/*.json; do
  python3 -c 'import json, sys; d = json.load(open(sys.argv[1])); assert d["uid"] and d["panels"]' \
    "$dashboard"
  echo "    $(basename "$dashboard") ok"
done

step "Image versions: docker-compose ↔ k8s"
# The same third-party image is pinned in docker-compose*.yml and in the manifests; a bump on
# one side only would make the two setups (and the config validation above) diverge.
python3 - "$OUT/local.yaml" docker-compose.yml docker-compose.infra.yml <<'PY'
import re
import sys


def images(path):
    found = {}
    for ref in re.findall(r'^\s*(?:-\s*)?image:\s*["\']?([^\s"\']+)', open(path).read(), re.M):
        if "${" in ref or "fate-" in ref:  # our own app images, tagged per environment
            continue
        repo, sep, tag = ref.rpartition(":")
        if not sep or "/" in tag:  # no tag (the colon belonged to a registry port)
            repo, tag = ref, "latest"
        found.setdefault(repo, set()).add(tag)
    return found


k8s = images(sys.argv[1])
compose = {}
for path in sys.argv[2:]:
    for repo, tags in images(path).items():
        compose.setdefault(repo, set()).update(tags)

errors = [f"{side}: {repo} is pinned to several tags {sorted(tags)}"
          for side, found in (("k8s", k8s), ("compose", compose))
          for repo, tags in sorted(found.items()) if len(tags) > 1]
shared = sorted(k8s.keys() & compose.keys())
errors += [f"{repo}: compose {sorted(compose[repo])} vs k8s {sorted(k8s[repo])}"
           for repo in shared if compose[repo] != k8s[repo]]
for error in errors:
    print(f"    MISMATCH {error}")
if errors:
    sys.exit(1)
print(f"    {len(shared)} shared images in sync")
PY

step "Gateway nginx config ($(image_of nginx))"
for conf in infra/nginx/nginx.conf .tilt-build/nginx.conf .e2e-build/nginx.conf; do
  # Upstreams are resolved at load time; point them anywhere.
  docker run --rm -v "$ROOT/$conf:/etc/nginx/nginx.conf:ro" \
    --add-host backend:127.0.0.1 --add-host frontend:127.0.0.1 --add-host keycloak:127.0.0.1 \
    --entrypoint nginx "$(image_of nginx)" -t -q
  echo "    $conf ok"
done

# ── 5. Scripts ──────────────────────────────────────────────────────────────
step "shellcheck"
docker run --rm -v "$ROOT:/mnt:ro" -w /mnt "$SHELLCHECK_IMAGE" \
  k8s-start.sh k8s-stop.sh infra/k8s/check.sh infra/k8s/e2e.sh infra/k8s/overlays/local/write-secrets.sh \
  infra/k8s/overlays/e2e/prepare.sh infra/keycloak/apply-config.sh infra/keycloak/create-database.sh
echo "    ok"

printf '\nAll Kubernetes checks passed.\n'
