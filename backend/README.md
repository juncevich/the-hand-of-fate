# Backend

Kotlin + Spring Boot REST API and gRPC server for The Hand of Fate.

```
  Frontend / Nginx          Telegram Bot (Go)
        │                          │
        │ HTTP/REST :8080           │ gRPC :9090
        ▼                          ▼
  ┌─────────────────────────────────────────┐
  │            Spring Boot App              │
  │                                         │
  │  web/auth/      /api/v1/auth/*          │
  │  web/vote/      /api/v1/votes/*         │
  │  web/telegram/  /api/v1/telegram/*      │
  │  grpc/          FateGrpcService         │
  │                                         │
  │  service/   AuthService  VoteService    │
  │             DrawService  EmailService   │
  └──────────────────┬──────────────────────┘
                     │ JPA / Flyway
                     ▼
              ┌─────────────┐
              │  PostgreSQL  │
              │    :5432     │
              └─────────────┘
```

## Tech Stack

| Component          | Version  |
|--------------------|----------|
| Kotlin             | 2.4.20   |
| Spring Boot        | 4.1.1    |
| Java (toolchain)   | 26       |
| gRPC / gRPC-Kotlin | 1.84.1 / 1.5.0 |
| Protobuf           | 4.36.2   |
| Access tokens      | Keycloak's RS256 JWTs, verified by the Spring Security OAuth2 resource server (Nimbus, JWKS) |
| PostgreSQL driver  | 42.7.14  |
| Flyway             | (managed by Spring Boot) |

## Project Structure

```
src/main/kotlin/com/juncevich/fate/      one package per Spring Modulith module;
│                                          its base package is the module's API, sub-packages are private
├── account/        AuthenticatedUser, UserProfile, UserQueryService, TelegramLinkService, AccountDeleted
│   ├── keycloak/   KeycloakAdminClient (Admin API), KeycloakProperties
│   ├── token/      JwtConfig, KeycloakTokens — access-token validation and conversion
│   ├── service/    provisioning, avatars, account deletion, sessions, token purge
│   ├── port/ persistence/ domain/
│   └── web/        /api/v1/auth, /api/v1/users/me, avatars, /api/v1/telegram
├── vote/           VoteService, VoteStatsService, read models (VoteModels.kt), VoteMode/Status/DrawTarget
│   ├── domain/     Vote, VoteParticipant, VoteOption, DrawHistory, DrawService, WinnerPicker, events
│   ├── listener/   AccountDeletionListener — forgets a deleted account in other people's votes
│   ├── notification/  NotificationRecorder — vote events → outbox jobs
│   ├── port/ persistence/
│   └── web/        /api/v1/votes — CRUD, draw, history, options
├── notification/   NotificationOutbox, EmailBudget, EmailPreferences, versioned payloads
│   ├── delivery/   NotificationWorker, NotificationAdapter, EmailService, NotificationProperties
│   ├── port/ persistence/
│   └── web/        /api/v1/notifications — unsubscribe
├── storage/        ObjectStorage port
│   └── s3/         S3 adapter, disabled fallback, configuration
├── grpc/           FateGrpcService (bot API over account + vote), error mapping, shared-secret auth
├── shared/         ErrorCode, FateException, validation, uuidV7 — depends on nothing
└── platform/       application wiring: SecurityConfig, ErrorHandler, OpenApiConfig, SecretsValidator
src/main/resources/
├── application.yml
└── db/migration/               Flyway migrations
```

## REST API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/v1/users/me` | Profile of the signed-in account (created on the first request with a Keycloak token) |
| `POST` | `/api/v1/auth/logout-all` | End every Keycloak session of the account |
| `POST` | `/api/v1/auth/delete-account` | Delete the account and the votes it created (needs a sign-in within 5 minutes) |
| `GET`/`PUT` | `/api/v1/users/me/email-preferences` | Notification emails on/off for the account's address |
| `POST` | `/api/v1/notifications/unsubscribe` | Opt an address out with the signed link from an email (public) |
| `POST` | `/api/v1/notifications/unsubscribe/one-click` | RFC 8058 one-click unsubscribe for mail clients (public) |
| `GET`  | `/api/v1/votes/stats` | `createdCount` — votes the user created |
| `GET`  | `/api/v1/votes` | List own votes (paginated) |
| `POST` | `/api/v1/votes` | Create a vote |
| `GET`  | `/api/v1/votes/{id}` | Vote details |
| `DELETE` | `/api/v1/votes/{id}` | Delete a vote |
| `POST` | `/api/v1/votes/{id}/draw` | Perform a draw |
| `GET`  | `/api/v1/votes/{id}/history` | Latest 100 entries (compatibility endpoint) |
| `GET`  | `/api/v1/votes/{id}/history/page?page=0&size=20` | Paginated history, maximum page size 100 |
| `POST` | `/api/v1/votes/{id}/options` | Add a named option |
| `DELETE` | `/api/v1/votes/{id}/options/{optionId}` | Remove an option |
| `PUT`  | `/api/v1/votes/{id}/options/order` | Reorder options (`{"optionIds": [...]}`, every option exactly once) |
| `GET`  | `/api/v1/telegram/link-token` | Generate a one-time Telegram link token |
| `PUT`  | `/api/v1/users/me/avatar` | Upload an avatar (`multipart/form-data`, part `file`; PNG/JPEG/GIF ≤ 2 MB) |
| `DELETE` | `/api/v1/users/me/avatar` | Remove the avatar |
| `GET`  | `/api/v1/users/{id}/avatar?v=…` | Avatar image (public, 256×256 PNG; immutable cache for the current `v`, ETag otherwise) |

Interactive docs: http://localhost:8080/swagger-ui.html (dev profile only).

The full contract is committed as [`api/openapi.yaml`](../api/openapi.yaml) and generated from the
controllers by springdoc (`platform/OpenApiConfig.kt`): bearer auth by default,
`@SecurityRequirements` (empty) on public endpoints, `ProblemDetail` as the error body, and Kotlin
constructor parameters without a default value marked `required`. After changing an endpoint or a
DTO, run `./gradlew openApiUpdate` (or `make openapi` from the repo root, which also regenerates the
frontend types and simulation models) and commit the result — `OpenApiSpecIntegrationTest` fails the
build when the file is stale.

## Running Locally

Requires PostgreSQL on `localhost:5432` and, for avatars, an S3-compatible store on `localhost:9000`.
Start both (plus Mailpit) with `make infra` from the repo root — the store is RustFS
(console http://localhost:9001, `rustfsadmin` / `rustfsadmin`); the dev profile creates the `fate` bucket.

```bash
./gradlew bootRun
```

**Demo user** (Keycloak dev realm, `infra/keycloak/realm-dev/`): `admin@admin.com` / `admin123`. Sign-in needs Keycloak — `make infra` starts it on :8180, served to the SPA under `:3000/auth`.

## Build Commands

```bash
./gradlew bootJar                           # fat JAR → build/libs/
./gradlew test                              # all unit tests
./gradlew test --tests "*.DrawServiceTest"  # single class
./gradlew generateProto                     # regenerate gRPC stubs from proto/
./gradlew openApiUpdate                     # rewrite ../api/openapi.yaml from the controllers (Docker)
```

## Database Migrations

Flyway runs automatically on startup. Files in `src/main/resources/db/migration/`:

| Version | Schema Change |
|---------|---------------|
| V1 | `users` table |
| V2 | `votes` table; `vote_mode` and `vote_status` enums |
| V3 | `vote_participants` table |
| V4 | `draw_history` table |
| V5 | `refresh_tokens` table |
| V6 | `telegram_link_tokens` table |
| V7 | Demo user seed (`admin@admin.com`) |
| V8 | `vote_options` table; `winner_option_id` / `winner_option_title` in draw_history; `winner_email` nullable |
| V9–V14 | Vote version, draw-history indexes, notification outbox, canonical emails, outbox dead-letter state |
| V15 | `users.avatar_updated_at` (avatar version; the image lives in object storage) |
| V16 | Drop Telegram links to group chats, `CHECK (telegram_id > 0)` |
| V17 | `email_budget_ledger` — per-user notification email budget |
| V18 | Refresh-token families: `family_id`, `revoked_at` (rotation with reuse detection) |
| V19 | Drop indexes duplicated by UNIQUE constraints or composite indexes |
| V20 | `votes.draw_target` — PARTICIPANTS or OPTIONS, fixed at creation |
| V21 | Option positions `0..n-1`, deferrable `UNIQUE (vote_id, position)` |
| V22 | `draw_history.seed` / `candidates` — draw audit record |
| V23 | `users.email_verified_at` + `email_tokens` — email verification and password reset |
| V24 | `email_suppressions` — addresses that unsubscribed from notification emails |
| V25 | `ON UPDATE CASCADE` on FKs to `users(id)` + `deleted_accounts` — Keycloak account takeover and deletion |
| V26 | Drops the app's own login: `refresh_tokens`, `email_tokens`, `users.password_hash`, `users.email_verified_at` |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/fate` | JDBC URL |
| `DB_USERNAME` | — | PostgreSQL user |
| `DB_PASSWORD` | — | PostgreSQL password |
| `KEYCLOAK_URL` | `http://localhost:8180/auth` | Where the backend reaches Keycloak (JWKS, Admin API) |
| `KEYCLOAK_PUBLIC_URL` | `http://localhost:3000/auth` | Keycloak's public base URL; accepted tokens have `iss` = `<this>/realms/fate` |
| `KEYCLOAK_CLIENT_SECRET` | (dev default) | Secret of the `fate-backend` service account; checked outside `dev`/`test` |
| `MAIL_HOST` | `localhost` | SMTP host (Mailpit in dev) |
| `MAIL_PORT` | `1025` | SMTP port |
| `FRONTEND_URL` | `http://localhost:3000` | CORS origin and email links |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` | OpenTelemetry collector |
| `S3_ENABLED` | `false` (`true` in `dev`) | Object storage for avatars; when off, avatar uploads answer 409 |
| `S3_ENDPOINT` | `http://localhost:9000` | S3 API endpoint (RustFS locally, any S3-compatible service in production) |
| `S3_REGION` / `S3_BUCKET` | `us-east-1` / `fate` | Bucket location and name |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | — (`rustfsadmin` in `dev`) | Required when storage is enabled |
| `S3_PATH_STYLE` | `true` | Path-style addressing; set `false` for providers that require virtual-hosted style |

## Testing Patterns

- All service tests are pure unit tests using MockK — no Spring context needed
- Controller tests use `MockMvcBuilders.standaloneSetup()` (Spring Boot 4 removed `@WebMvcTest`)
- Register `AuthenticationPrincipalArgumentResolver` and `PageableHandlerMethodArgumentResolver` explicitly
- Set `SecurityContextHolder` directly in `@BeforeEach` for endpoints using `@AuthenticationPrincipal`
- `PageImpl` must be constructed with three args: `PageImpl(content, pageable, total)`
- `ErrorHandler` masks exception details — tests assert `"Internal server error"`, not the original message

### Verifying a draw

Every draw since V22 stores its seed and its candidates in canonical order (participant emails or
option ids, sorted). To check a recorded winner:

```sql
SELECT seed, candidates, winner_email, winner_option_id FROM draw_history WHERE id = '<draw id>';
```

then `candidates[WinnerPicker.index(seed, candidates.size)]` (0-based) must equal the winner's email or
option id. `WinnerPicker.index` takes the first 63 bits of SHA-256(seed bytes ‖ counter as a big-endian
int) for counter = 0, 1, …, skips values from the incomplete last block of `size` and returns the value
modulo `size` — a few lines in any language (`WinnerPickerTest` has golden values). The record shows the
winner follows from the seed; it cannot show the seed was not chosen after the fact, which would need a
commit-reveal scheme.

### Consistency and notification delivery

All vote mutations acquire the same PostgreSQL row lock. Telegram link tokens are locked before consumption. Email addresses are normalized in the service layer before lookup, deduplication and persistence. The vote module uses immutable `UserProfile` values without credential fields.

Migration V11 adds `notification_outbox` (V14 adds the `failed_at` dead-letter state). Vote events are recorded in the vote transaction, with one job per recipient. Payloads are dedicated versioned DTOs (`InvitationPayload`, `DrawResultPayload`, `NOTIFICATION_PAYLOAD_VERSION`), not the domain events, so refactoring an event does not break queued jobs; an unreadable or unsupported-version payload is marked failed immediately. `NotificationWorker` polls every second, claims at most `batch-size` jobs, and performs SMTP delivery outside DB transactions. A claimed job is leased (`lease`, default 2 minutes) so a crashed worker's job is recovered. The worker is designed for a single backend instance: under heavy SMTP retries a batch can outlive the lease, and a second instance would re-claim and send duplicates. SMTP connection/read/write timeouts are 5/10/10 seconds.

Failed jobs are retried with exponential backoff: `retry-base-delay` (1 minute) doubling per attempt up to `retry-max-delay` (6 hours). After `max-attempts` (10, about 8.5 hours of retries) the job gets `failed_at` and keeps `last_error` for investigation; this increments `notification.dead{type}`, while every failed attempt increments `notification.failed{type}`. Failed jobs are deleted after `failed-retention` (14 days). After resolving the cause, operators can requeue a selected job by setting `failed_at = NULL`, `attempts = 0`, `available_at = now()` and `claim_id = NULL`. Successful jobs are deleted. Delivery is at least once: a crash after SMTP accepts a message but before acknowledgment can cause a duplicate.

Each user's actions may trigger at most `email-budget-per-user` (200) emails per rolling `email-budget-window` (24 hours), counting invitations and every draw-result recipient (`EmailBudget`, ledger table from V17, serialized per user with a transaction-scoped advisory lock). An invitation that does not fit is rejected with 429 / `RESOURCE_EXHAUSTED` and the whole request rolls back; a draw that does not fit still happens, but its result emails are skipped and counted in `notification.suppressed`. Deleting votes or participants does not return budget.

`app.notifications.delivery-enabled=false` disables delivery while retaining durable recording. `app.notifications.poll-delay-ms` controls the poll interval (default 1000) and `app.notifications.purge-delay-ms` the failed-job purge interval (default 1 hour); the remaining settings live in `NotificationProperties`. Integration tests disable automatic delivery except tests that verify the complete delivery flow.

Migration V12 adds an index for stable history pagination by `(vote_id, drawn_at DESC, id DESC)`.

Migration V13 canonicalizes existing participant emails, merges aliases of the same address within each vote (keeping the earliest entry), and canonicalizes recorded participant wins for fair rotation.
