#!/bin/sh
# Creates Keycloak's role and database next to the app's `fate` database, idempotently — a
# Postgres volume that already exists never re-runs docker-entrypoint init scripts.
# Runs in a postgres image as the superuser: PGHOST, PGUSER, PGPASSWORD, KC_DB_PASSWORD.
set -eu

until pg_isready -q -d fate; do sleep 1; done

psql -v ON_ERROR_STOP=1 -v pw="$KC_DB_PASSWORD" -d fate <<'SQL'
SELECT format('CREATE ROLE keycloak LOGIN PASSWORD %L', :'pw')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'keycloak')\gexec
SELECT 'CREATE DATABASE keycloak OWNER keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
SQL
echo "keycloak database ready"
