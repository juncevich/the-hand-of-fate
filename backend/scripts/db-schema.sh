#!/usr/bin/env bash
# Writes the schema the Flyway migrations produce to backend/db-schema/schema.sql — one DDL file for
# IntelliJ's DDL data source (its SQL and JPA inspections). The migrations themselves don't work
# there: the IDE applies them in name order (V10 before V1, V26 before V3) and misses some drops.
# Not under build/: the IDE excludes that directory and a DDL data source reads nothing from it.
#
# Applies every migration in version order to a throwaway PostgreSQL container, as Flyway would
# (each file in one transaction, placeholder seedDemoUser=false), then dumps the schema only.
# Run it again after adding a migration. Needs Docker.
#
#   backend/scripts/db-schema.sh            # or: make db-schema
#   POSTGRES_IMAGE=postgres:17.11-alpine backend/scripts/db-schema.sh
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS="$BACKEND_DIR/src/main/resources/db/migration"
OUT="$BACKEND_DIR/db-schema/schema.sql"
# The integration tests' image (Testcontainers), usually already pulled.
IMAGE="${POSTGRES_IMAGE:-postgres:17}"
CONTAINER="fate-db-schema-$$"

cleanup() { docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --rm --name "$CONTAINER" -e POSTGRES_PASSWORD=schema -e POSTGRES_DB=fate "$IMAGE" >/dev/null

# Over TCP: while the image initializes the database, a temporary server answers on the unix
# socket only and is then restarted, so a socket check can pass too early.
ready() { docker exec "$CONTAINER" pg_isready -h 127.0.0.1 -U postgres -d fate -q; }
for _ in $(seq 60); do
  ready && break
  sleep 1
done
ready || { echo "PostgreSQL did not start" >&2; exit 1; }

# Flyway orders by version number, not by file name.
count=0
while IFS= read -r file; do
  # shellcheck disable=SC2016 # a literal ${seedDemoUser}, Flyway's placeholder syntax
  sed 's/\${seedDemoUser}/false/g' "$file" |
    docker exec -i "$CONTAINER" psql -q -X -v ON_ERROR_STOP=1 --single-transaction -U postgres -d fate >/dev/null
  count=$((count + 1))
done < <(find "$MIGRATIONS" -name 'V*__*.sql' | awk -F/ '{ print $NF "\t" $0 }' | sort -V | cut -f2)

mkdir -p "$(dirname "$OUT")"
# Plain DDL only: no session settings, psql meta-commands or dump comments. Names go unqualified,
# as in the migrations and the app's SQL — the IDE resolves `users`, not `public.users`.
docker exec "$CONTAINER" pg_dump -U postgres -d fate --schema-only --no-owner --no-privileges |
  grep -Ev '^(SET |SELECT pg_catalog\.set_config|\\|--)' |
  sed 's/public\.//g' |
  cat -s > "$OUT"

echo "Applied $count migrations; schema written to backend/db-schema/schema.sql"
