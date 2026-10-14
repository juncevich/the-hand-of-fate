#!/usr/bin/env bash
# Writes .secrets.env (gitignored) next to this script from the repository's .env, for the
# `shared-secrets` generator in kustomization.yaml. Called by k8s-start.sh and the Tiltfile.
# An unset or placeholder BOT_TOKEN is written empty; callers then leave the bot out.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/../../../.." && pwd)"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
BOT_TOKEN="${BOT_TOKEN:-}"
[[ "$BOT_TOKEN" == "your_telegram_bot_token_here" ]] && BOT_TOKEN=""

umask 077
cat >"$DIR/.secrets.env" <<EOF
GRPC_SHARED_SECRET=${GRPC_SHARED_SECRET:-dev-grpc-shared-secret-for-testing-only}
BOT_TOKEN=${BOT_TOKEN}
EOF
