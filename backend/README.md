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
| Kotlin             | 2.3.21   |
| Spring Boot        | 4.0.6    |
| Java               | 21       |
| gRPC / gRPC-Kotlin | 1.80.0 / 1.5.0 |
| Protobuf           | 4.34.1   |
| JJWT               | 0.13.0   |
| PostgreSQL driver  | 42.7.10  |
| Flyway             | (managed by Spring Boot) |

## Project Structure

```
src/main/kotlin/com/juncevich/fate/
├── config/
│   ├── JwtProperties.kt        JWT TTL + secret config
│   └── SecurityConfig.kt       Spring Security — public vs protected routes
├── security/
│   ├── JwtTokenProvider.kt     Token generation and validation
│   └── JwtAuthFilter.kt        Reads Bearer token from each request
├── web/
│   ├── auth/                   /api/v1/auth — register, login, refresh, logout
│   ├── vote/                   /api/v1/votes — CRUD, draw, history, options
│   ├── telegram/               /api/v1/telegram — link token generation
│   └── common/                 ErrorHandler — maps exceptions to RFC 7807 responses
├── service/
│   ├── AuthService.kt          Registration, login, token refresh/revocation
│   ├── VoteService.kt          Vote CRUD, addOption / removeOption
│   ├── DrawService.kt          SIMPLE / FAIR_ROTATION draw logic for participants and options
│   ├── NotificationService.kt  Async email dispatch (never blocks draw success)
│   ├── EmailService.kt         HTML email rendering and sending
│   └── TelegramLinkService.kt  One-time token creation and validation
├── domain/
│   ├── user/                   User entity + repository
│   ├── vote/                   Vote, VoteParticipant, VoteOption, DrawHistory entities + repos
│   └── auth/                   RefreshToken, TelegramLinkToken entities + repos
└── grpc/
    └── FateGrpcService.kt      gRPC implementation — delegates to service layer
src/main/resources/
├── application.yml
└── db/migration/               Flyway migrations V1–V8
```

## REST API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/auth/register` | Register a new user |
| `POST` | `/api/v1/auth/login` | Login, returns access + refresh tokens |
| `POST` | `/api/v1/auth/refresh` | Refresh access token |
| `POST` | `/api/v1/auth/logout` | Revoke refresh token |
| `GET`  | `/api/v1/votes` | List own votes (paginated) |
| `POST` | `/api/v1/votes` | Create a vote |
| `GET`  | `/api/v1/votes/{id}` | Vote details |
| `DELETE` | `/api/v1/votes/{id}` | Delete a vote |
| `POST` | `/api/v1/votes/{id}/draw` | Perform a draw |
| `GET`  | `/api/v1/votes/{id}/history` | Draw history |
| `POST` | `/api/v1/votes/{id}/options` | Add a named option |
| `DELETE` | `/api/v1/votes/{id}/options/{optionId}` | Remove an option |
| `GET`  | `/api/v1/telegram/link-token` | Generate a one-time Telegram link token |

Interactive docs: http://localhost:8080/swagger-ui.html

## Running Locally

Requires PostgreSQL on `localhost:5432`. Start it with `make infra` from the repo root.

```bash
./gradlew bootRun
```

**Demo user** (V7 migration): `admin@admin.com` / `admin`

## Build Commands

```bash
./gradlew bootJar                           # fat JAR → build/libs/
./gradlew test                              # all unit tests
./gradlew test --tests "*.DrawServiceTest"  # single class
./gradlew generateProto                     # regenerate gRPC stubs from proto/
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

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/fate` | JDBC URL |
| `DB_USERNAME` | — | PostgreSQL user |
| `DB_PASSWORD` | — | PostgreSQL password |
| `JWT_ACCESS_SECRET` | (dev default) | Must be ≥256-bit in production |
| `JWT_ACCESS_TTL_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_TTL_DAYS` | `30` | Refresh token lifetime |
| `MAIL_HOST` | `localhost` | SMTP host (MailHog in dev) |
| `MAIL_PORT` | `1025` | SMTP port |
| `FRONTEND_URL` | `http://localhost:3000` | CORS origin and email links |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` | OpenTelemetry collector |

## Testing Patterns

- All service tests are pure unit tests using MockK — no Spring context needed
- Controller tests use `MockMvcBuilders.standaloneSetup()` (Spring Boot 4 removed `@WebMvcTest`)
- Register `AuthenticationPrincipalArgumentResolver` and `PageableHandlerMethodArgumentResolver` explicitly
- Set `SecurityContextHolder` directly in `@BeforeEach` for endpoints using `@AuthenticationPrincipal`
- `PageImpl` must be constructed with three args: `PageImpl(content, pageable, total)`
- `ErrorHandler` masks exception details — tests assert `"Internal server error"`, not the original message
