---
name: new-migration
description: Use when adding a new Flyway migration under backend/src/main/resources/db/migration — determines the next version number, follows this project's conventions (enum types, idempotent seeds, nullable/versioning), and flags related code that must change alongside it.
---

# new-migration

Adds a new Flyway migration to `backend/src/main/resources/db/migration/` following this project's established conventions.

## Steps

1. **List existing migrations** to find the next version number:
   ```bash
   ls backend/src/main/resources/db/migration/
   ```
   Current history (from CLAUDE.md, verify against actual files — this list can be stale):
   - V1 users, V2 votes (enum types), V3 vote_participants, V4 draw_history, V5 refresh_tokens,
     V6 telegram_link_tokens, V7 demo user seed (placeholder-gated), V8 vote_options
   - Next migration is `V{n+1}__description.sql` — snake_case description, matches existing filename style exactly.

2. **Check if the change needs a domain-level companion change**, not just SQL:
   - New/changed table → new JPA entity in `<module>/internal/persistence/entity/`, repository in `.../jpa/`, adapter in `.../adapter/`, mapper in `.../mapper/`, and a plain domain class in `.../domain/` (no JPA annotations there — hexagonal architecture, see CLAUDE.md).
   - New enum type → mirror it in Kotlin (see how `vote_mode` / `vote_status` are handled from V2).
   - Seed/test-only data → gate behind a Flyway placeholder like `seedDemoUser` (V7 pattern): default `false`, set `true` only for `dev`/`test` profiles in the relevant `application-*.yml`. Never seed data unconditionally in a migration that runs in production.

3. **Idempotency**: seed/data migrations should use idempotent inserts (`ON CONFLICT DO NOTHING` or equivalent) — follow the V7 pattern if touching seed data.

4. **Backwards compatibility of schema changes**: check whether existing columns need to become nullable or get defaults for zero-downtime deploys (see V8 making `winner_email` nullable when introducing `winner_option_id`).

5. **After writing the migration**, run the backend test suite — Flyway runs migrations against a real/test Postgres via Testcontainers or the configured test datasource:
   ```bash
   cd backend && ./gradlew test
   ```

6. **Run `ModularityTest`** if the change touches entity/adapter placement across module boundaries:
   ```bash
   ./gradlew test --tests "*.ModularityTest"
   ```

## Gotchas

- Never edit a previously-applied migration file — Flyway checksums it. Always add a new `V{n}` file, even to fix a mistake in an existing one.
- Production never sets `seedDemoUser` (or similar dev-only placeholders) — double check any new placeholder defaults to the safe/off value.
