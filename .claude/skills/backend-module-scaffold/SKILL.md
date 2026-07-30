---
name: backend-module-scaffold
description: Use when adding a new REST endpoint, service, or persistence flow to the Kotlin/Spring Boot backend — follows this repo's hexagonal (ports & adapters) module layout and MockK/standaloneSetup test conventions so new code matches existing modules (auth, vote, shared, grpc).
---

# backend-module-scaffold

Scaffolds new backend functionality consistent with the hexagonal architecture enforced by Spring Modulith in this repo.

## Layout convention (per module: `auth`, `vote`, `shared`, `grpc`)

```
<module>/
  internal/
    domain/        — plain Kotlin classes, NO JPA annotations
    port/          — repository/notification interfaces
    persistence/
      entity/      — JPA entities
      jpa/         — Spring Data JPA repositories
      adapter/     — JPA adapter implementations of port interfaces
      mapper/      — domain ↔ entity mappers
    web/           — REST controllers and DTOs
    notification/  — notification adapters (e.g. email via NotificationPort)
```

`internal/` is private to the module — do not reach into another module's `internal` package from outside it.

## Steps

1. **Identify the target module** (`auth`, `vote`, `shared`, `grpc`) — new functionality should live in the module that owns the domain concept, not wherever is convenient.
2. **Domain first**: define/extend the plain domain class in `domain/` — no persistence concerns leak in here.
3. **Port**: define the interface in `port/` if the domain needs to talk to persistence or notifications.
4. **Persistence**: JPA entity in `persistence/entity/`, Spring Data repo in `persistence/jpa/`, adapter implementing the port in `persistence/adapter/`, mapper in `persistence/mapper/`.
5. **Web**: controller + request/response DTOs in `web/`. Follow existing controllers for auth patterns (`@AuthenticationPrincipal`, pagination via `Pageable`).
6. **Service**: orchestrates domain + ports; look at `DrawService` / `VoteService` for the level of logic expected here (e.g. SIMPLE vs FAIR_ROTATION branching lives in the service, not the controller).
7. **Optimistic locking**: if the entity can be concurrently mutated (like `Vote`), add `@Version` and handle `OptimisticLockException` appropriately.
8. **Metrics**: if this is a notable business event, add a Micrometer counter following the `vote.created{mode}` / `vote.draw.performed{mode,round}` naming pattern.

## Tests (MockK, no Spring context)

- Service tests: pure unit tests with MockK — do not spin up a Spring context.
- Controller tests: use `MockMvcBuilders.standaloneSetup()` (Spring Boot 4.0 removed `@WebMvcTest`).
  - Explicitly register `AuthenticationPrincipalArgumentResolver` and `PageableHandlerMethodArgumentResolver` if the controller uses `@AuthenticationPrincipal` or `Pageable`.
  - Set `SecurityContextHolder` directly in `@BeforeEach` for `@AuthenticationPrincipal` endpoints.
  - Include ALL required non-null JSON fields in request bodies — Kotlin default params don't apply at the JSON/Jackson 3.x level.
  - Construct `PageImpl` as `PageImpl(content, pageable, total)` — the single-arg constructor breaks serialization.
- Generic error responses: `ErrorHandler`'s 500 handler masks exception details — assert on `"Internal server error"` title, not the original exception message.
- After scaffolding, run `./gradlew test --tests "*.ModularityTest"` to confirm module boundaries weren't violated.

## Formatting / lint

```bash
cd backend
./gradlew spotlessApply   # auto-fix formatting (ktlint)
./gradlew detekt          # static analysis
```
