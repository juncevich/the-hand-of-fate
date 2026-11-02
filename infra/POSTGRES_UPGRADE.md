# PostgreSQL 17 → 18

Compose, Kubernetes and integration tests now use `postgres:18.6-alpine`.
Existing PostgreSQL 17 data needs dump/restore or `pg_upgrade`; changing the image
does not convert a database. Both the `fate` and `keycloak` databases must move.
See the [PostgreSQL migration notes](https://www.postgresql.org/docs/18/release-18.html#RELEASE-18-MIGRATION).

## Persistent storage

The repository deliberately keeps the existing volume mount and explicitly sets
`PGDATA`: `/var/lib/postgresql/data` in Compose and
`/var/lib/postgresql/data/pgdata` in Kubernetes. PostgreSQL 18 otherwise defaults
to `/var/lib/postgresql/18/docker`. Keeping the old location ensures an old
cluster is detected and rejected as incompatible, rather than silently starting
an empty database at a different location. Fresh volumes initialize normally.

## Preserve an existing local database

1. Stop writers (backend, bot and Keycloak). Keep PostgreSQL 17 running using
   the previous checkout or an image override; do this before starting the new
   stack. Do not delete the old volume or PVC.
2. Dump all databases and roles from the running PostgreSQL 17 server:

   ```bash
   # Full Compose stack; add -f docker-compose.infra.yml for the native-app setup.
   docker compose exec -T postgres pg_dumpall -U fate > postgres17-backup.sql
   # Or Kubernetes:
   kubectl --context hand-of-fate -n hand-of-fate exec postgres-0 -- \
     pg_dumpall -U fate > postgres17-backup.sql
   ```

3. Stop PostgreSQL 17. Create a separate empty volume/PVC for PostgreSQL 18 and
   point the stack at it. Keep the old storage intact for rollback. For Compose,
   a local override can select a different named volume:

   ```yaml
   volumes:
     postgres_data:
       name: hand-of-fate-postgres18
   ```

4. Start only PostgreSQL 18 and restore with `psql -X -U fate -d postgres`,
   feeding it `postgres17-backup.sql`. Inspect the restore output: the existing
   bootstrap `fate` role/database may produce already-exists messages; other
   errors need investigation. Confirm both databases and their ownership, then
   run `vacuumdb -U fate --all --analyze-in-stages`.
5. Start Keycloak and the applications, verify sign-in, existing votes and draws.
   Retain the dump and PostgreSQL 17 storage until the migration is confirmed.

The server setup workflow installs PostgreSQL 18 on a fresh VPS. It leaves an
existing PostgreSQL installation in place; migrate that installation separately.
Dependency-update checks use disposable Testcontainers and do not migrate local
Compose or Kubernetes databases.
