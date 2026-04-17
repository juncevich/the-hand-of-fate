#!/usr/bin/env bash
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Stopping app processes..."
pkill -f "gradlew bootRun"  2>/dev/null && echo "    backend stopped."  || true
pkill -f "FateApplicationKt" 2>/dev/null || true
lsof -ti :8080              2>/dev/null | xargs kill -9 2>/dev/null || true
pkill -f "vite"             2>/dev/null && echo "    frontend stopped." || true
lsof -ti :3000              2>/dev/null | xargs kill -9 2>/dev/null || true
pkill -f "go run ./cmd/bot" 2>/dev/null && echo "    bot stopped."      || true

echo "==> Stopping infrastructure..."
docker compose -f "$ROOT/docker-compose.infra.yml" down

echo "==> Done."
