#!/usr/bin/env bash
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load .env if present (for BOT_TOKEN etc.)
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

# ── Kill stale processes from previous runs ─────────────────────────────────
pkill -f "gradlew bootRun"   2>/dev/null || true
pkill -f "FateApplicationKt" 2>/dev/null || true
lsof -ti :8080               2>/dev/null | xargs kill -9 2>/dev/null || true
pkill -f "vite"              2>/dev/null || true
lsof -ti :3000               2>/dev/null | xargs kill -9 2>/dev/null || true
pkill -f "go run ./cmd/bot"  2>/dev/null || true

# ── Infrastructure ──────────────────────────────────────────────────────────
echo "==> Starting infrastructure (postgres, mailhog)..."
docker compose -f "$ROOT/docker-compose.infra.yml" up -d

echo "==> Waiting for postgres to be ready..."
until docker compose -f "$ROOT/docker-compose.infra.yml" exec -T postgres \
  pg_isready -U fate -d fate &>/dev/null; do
  sleep 1
done
echo "    postgres is ready."

# ── App processes ───────────────────────────────────────────────────────────
PIDS=()

cleanup() {
  echo ""
  echo "==> Stopping app processes..."
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
  echo "==> Done. Infrastructure is still running (docker compose -f docker-compose.infra.yml down to stop)."
}
trap cleanup EXIT INT TERM

# Colors for log prefixes
RED='\033[0;31m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; RESET='\033[0m'

run_service() {
  local name="$1" color="$2" dir="$3"
  shift 3
  (
    cd "$dir"
    "$@" 2>&1 | while IFS= read -r line; do
      printf "${color}[%-8s]${RESET} %s\n" "$name" "$line"
    done
  ) &
  PIDS+=($!)
}

echo "==> Starting backend..."
run_service "backend" "$GREEN" "$ROOT/backend" \
  ./gradlew bootRun \
    -PjvmArgs="-DMAIL_HOST=localhost -DMAIL_PORT=1025 -DFRONTEND_URL=http://localhost:3000"

echo "==> Starting frontend..."
run_service "frontend" "$BLUE" "$ROOT/frontend" \
  npm run dev

if [[ -n "${BOT_TOKEN:-}" && "$BOT_TOKEN" != "your_telegram_bot_token_here" ]]; then
  echo "==> Starting bot..."
  run_service "bot" "$RED" "$ROOT/bot" \
    go run ./cmd/bot
else
  echo "==> Skipping bot (BOT_TOKEN not set in .env)"
fi

echo ""
echo "  Frontend  → http://localhost:3000"
echo "  Backend   → http://localhost:8080  (Swagger: /swagger-ui.html)"
echo "  MailHog   → http://localhost:8025"
echo ""
echo "  Press Ctrl+C to stop app processes."
echo ""

# Keep running until all processes exit or Ctrl+C
while true; do
  running=false
  for pid in "${PIDS[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      running=true
      break
    fi
  done
  "$running" || break
  sleep 2
done
