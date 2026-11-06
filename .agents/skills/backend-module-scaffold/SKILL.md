---
name: backend-module-scaffold
description: Use when adding a new REST endpoint, service, or persistence flow to the Kotlin/Spring Boot backend — follows this repo's hexagonal (ports & adapters) module layout and MockK/standaloneSetup test conventions so new code matches existing modules (account, vote, notification, storage, grpc, shared, platform).
---

# backend-module-scaffold

Scaffolds new backend functionality consistent with the hexagonal architecture enforced by Spring Modulith in this repo.

## Layout convention (business modules: `account`, `vote`, `notification`, `storage`)

```
<module>/          — the module's API: services other modules/adapters call, read models, events, enums
  domain/          — plain Kotlin classes, NO JPA annotations
  port/            — repository/external-system interfaces
  persistence/
    entity/        — JPA entities
    jpa/           — Spring Data JPA repositories
    adapter/       — JPA adapter implementations of port interfaces
    mapper/        — domain ↔ entity mappers
  web/             — REST controllers and DTOs
  <feature>/       — a feature sub-package where it reads better (e.g. `vote/listener`, `notification/delivery`)
```

Spring Modulith treats a module's **base package as its API and every sub-package as private** — there is no `internal/` level. Put implementation classes in a sub-package (never straight into the base package, or they become API), and never import another module's sub-packages. `shared` is the shared kernel (depends on nothing); `platform` holds application wiring (security, `ErrorHandler`, OpenAPI, secrets) and no module may depend on it.

## Steps

1. **Identify the target module** (`account`, `vote`, `notification`, `storage`; `grpc` only for the bot's API) — new functionality should live in the module that owns the domain concept, not wherever is convenient.
2. **Domain first**: define/extend the plain domain class in `domain/` — no persistence concerns leak in here.
3. **Port**: define the interface in `port/` if the domain needs to talk to persistence or notifications.
4. **Persistence**: JPA entity in `persistence/entity/`, Spring Data repo in `persistence/jpa/`, adapter implementing the port in `persistence/adapter/`, mapper in `persistence/mapper/`.
5. **Web**: controller + request/response DTOs in `web/`. Follow existing controllers for auth patterns (`@AuthenticationPrincipal`, pagination via `Pageable`).
6. **Service**: orchestrates only — load the aggregate (under its lock for a change), call its method, save it, then do what lies outside it (email budget, events, metrics). Business rules belong to the aggregate, as in `vote/domain/Vote.kt` (e.g. SIMPLE vs FAIR_ROTATION branching is `Vote.draw`); `VoteService` shows the expected thinness. Prefer one repository port per aggregate (`vote/domain/VoteRepository.kt`) over one per table, and test rules on the aggregate without mocks (`VoteTest`).
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
