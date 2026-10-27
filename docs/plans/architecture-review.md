# План исправлений по архитектурному ревью бэкенда

Источник: ревью архитектуры (Kotlin/Spring) от 2026-10-24. Пункты идут в порядке выполнения:
сначала локальные и безопасные, потом сквозные рефакторинги. После каждого блока —
`./gradlew spotlessApply detekt test` (и `integrationTest`, где затронута персистентность).

## 1. gRPC-слой
- [x] `grpcRead`/`grpcMutation`: не ловить `Throwable` через `runCatching` — ловить `Exception`, пробрасывать `CancellationException`
- [x] Перейти с `FateServiceCoroutineImplBase` на блокирующий `FateServiceImplBase` на виртуальных потоках (убрать корутинный диспетчер, `kotlinx-coroutines-reactor`, если больше не нужен)
- [x] Не создавать незакрываемые executor'ы на верхнем уровне (`FateGrpcService`, `GrpcServerConfig`)
  - заодно: proto-маппинги вынесены в `grpc/ProtoMappings.kt`, тесты транспорта — `FateGrpcServiceTransportTest`

## 2. Мелкая чистка
- [x] `WinnerPicker.pick` возвращает именованный `data class` вместо `Pair`
- [x] `Vote.eligibleThisRound` возвращает именованный тип вместо `Pair`
- [x] Убрать wildcard-импорты (`jakarta.persistence.*`, `com.juncevich.fate.vote.*`) — проектный `vote.*` в `main` убран; `jakarta.persistence.*`, `web.bind.annotation.*` и тестовые оставлены: это соглашение проекта (WildcardImport/no-wildcard-imports выключены в detekt.yml и .editorconfig)
- [x] Исправить устаревший комментарий про `internal/web` в `VoteModels.kt`
- [x] Поправить форматирование `if (row == null)` в `VoteRepositoryAdapter.syncParticipants`

## 3. Время
- [x] Бин `java.time.Clock` (в `platform`), внедрить во все сервисы/воркеры вместо `Instant.now()`
- [x] Домен получает время параметром (`Vote.create`, `invite`, `addOption`, `draw`, `TelegramLinkToken.isExpired`, `User`)
- [x] Один источник `created_at`: убрать `@CreatedDate`/`AuditingEntityListener` с сущностей, где время задаёт домен (проверить `@LastModifiedDate`/`updated_at`) — `updated_at` остался `@LastModifiedDate`, но auditing читает тот же `Clock` (`auditingDateTimeProvider`)
- [x] Тесты на время — через фиксированный `Clock`, без `mockkStatic`

  - `Instant.now()` остался только в `timestamp` тел ошибок (`ErrorHandler`, `shared/Problems.kt`) — это не бизнес-время

## 4. Чистота domain и API модулей
- [x] Порты `VoteRepository`/`DrawHistoryRepository` без `Page`/`Pageable` Spring Data (свои `PageQuery`/`PageResult` в `vote/domain/Paging.kt`; `VoteService` переводит `Pageable` ↔ `Page`, сортировка из запроса не учитывается — порядок задан запросами)
- [x] `WinnerPicker` без `@Component` в domain (бин в `vote/config/VoteDomainConfig.kt`)
- [x] `@Schema(enumAsRef)` убрать с доменных enum'ов, настроить через `OpenApiCustomizer` в `platform` (спека `api/openapi.yaml` не должна измениться) — глобальный `ModelResolver.enumsAsRef` в `OpenApiConfig`, описание `DrawTarget` в `vote/web/VoteOpenApiDocs`; спека не изменилась (`OpenApiSpecIntegrationTest`)
- [x] `UserQueryService` отдаёт наружу только `UserProfile`; `grpc` переходит на него; `User` переезжает в `account/domain` — `TelegramLinkService.linkAccount` тоже возвращает `UserProfile`, `MeController` читает порт `UserRepository`, правило в `ModularityTest` обобщено на все модули

## 5. Типобезопасные идентификаторы (value classes)
- [x] `Email` (нормализация — инвариант типа) в `shared` — `Email.of` (валидирует ввод) / `Email.trusted` (данные из БД и токена)
- [x] `VoteId`, `UserId` в API модулей; сигнатуры `VoteService`/агрегата/портов — `UserId` в `shared`, `VoteId` в `vote`; id опций/участников остались `UUID`, read models (`*View`) — на простых типах
- [x] Адаптеры: web (Jackson/Spring MVC path variables), gRPC, JPA (конвертеры или распаковка в адаптере) — распаковка на границе, wire-форматы не изменились; хелперы `AuthenticatedUser`/`UserProfile`.`userId`/`.mailbox`

## 6. Доменные события из агрегата
- [x] `Vote` накапливает события (`ParticipantInvited`, `VoteDrawn`), сервис публикует их после `save`
  - Сделано 2026-10-24 (сначала было отложено): события в терминах агрегата — `VoteCreated` / `ParticipantInvited` / `VoteDrawn` (id, адреса, `DrawOutcome`), `Vote.pullEvents()`, публикация в `VoteService.store`. Бюджет писем и имя создателя переехали из сервиса в `NotificationRecorder` — слоя перевода событий не понадобилось

## 7. Масштабирование NotificationWorker (по желанию)
- [x] Продление lease во время ретраев SMTP или ShedLock — только если появится второй инстанс
  - Сделано 2026-10-24: heartbeat `NotificationWorker.renewLeases` (каждые `app.notifications.lease-renewal`, 30 s) продлевает lease задач в работе через `NotificationOutboxPort.renew` (ограждение по `claim_id`); подтверждение сначала снимает задачу с продления; потерянный lease — warn + `notification.lease.lost`. ShedLock не нужен: несколько экземпляров разбирают очередь параллельно (`SKIP LOCKED`)

## 8. Повторное ревью (2026-10-24)
- [x] gRPC: логировать непредвиденные ошибки (`INTERNAL`) со стектрейсом, как REST; убрать неиспользуемый `GrpcErrors.clientStatus`
- [x] gRPC ↔ REST: одинаковый маппинг `ObjectOptimisticLockingFailureException` (`CONCURRENT_MODIFICATION`) и нарушения уникальности (`ALREADY_EXISTS`) — один перевод на оба транспорта, покрыть контрактным тестом — `shared.asClientError()` (`ClientErrors.kt`), `ErrorHandler.handleInfrastructure`, `ErrorMappingContractTest`, `GrpcErrorsTest`
- [x] Письма: шаблоны вместо HTML в строках `EmailService`, локализация ru/en; настройки почты и `app.frontend-url` — через `@ConfigurationProperties` вместо разрозненных `@Value`
  - FreeMarker `mail/templates/notification.ftlh` (автоэкранирование) + `mail/messages{,_ru}.properties`, рендерер `MailTemplates`; язык `app.mail.locale` (`MAIL_LOCALE`, по умолчанию `ru`) — один на всех получателей; `NotificationMailProperties` (`app.mail`) и `shared.FrontendProperties` вместо `@Value`. Оставлен `@Value` секрета отписки в `EmailPreferences`
- [x] Удаление аватара после коммита — `@TransactionalEventListener(AFTER_COMMIT)` на `AccountDeleted` вместо ручной `TransactionSynchronization`; `runCatching` в `AvatarService` → `catch (Exception)`
  - `account/avatar/DeletedAccountAvatarCleanup` (`AFTER_COMMIT`); `AvatarService.deleteObjectQuietly` ловит `Exception`; интеграционный тест: объект аватара исчезает после удаления аккаунта
- [x] `requireValidTitle` отклоняет управляющие символы (перевод строки и т. п.)
  - Код ошибки прежний (`TITLE_INVALID`), уточнены тексты во фронтенде и боте; реальный источник таких названий — перевод строки в `/newvote` бота
- [x] Собственный `AbstractAuthenticationToken` вместо `UsernamePasswordAuthenticationToken` для JWT
  - `account/keycloak/KeycloakAuthentication`: principal `AuthenticatedUser`, credentials — проверенный `Jwt`
- [ ] (по необходимости) кэш проверенных `sub` в `KeycloakAccessTokenConverter` — только при росте нагрузки, с учётом удаления аккаунта на других экземплярах
  - Не делаем, пока нет нагрузки (2026-10-24): лишний SELECT на запрос сейчас незаметен, а кэш при нескольких экземплярах продлил бы жизнь токенов удалённого аккаунта на других экземплярах — понадобилась бы инвалидация

## 9. Идиоматичность и свежие возможности (ревью 2026-10-27)

Быстрые правки:
- [x] KDoc контракта кодов над `enum class ErrorCode`, а не над `LEGACY_LOGIN`
- [x] Устаревшие комментарии в `build.gradle.kts` (NimbusJwtEncoder, «seeds the demo user, disables Secure cookies» у `bootRun`, «Kotlin 2.3.x» у spotless)
- [x] `VoteJpaRepository.findAllByUserIdOrParticipantEmail`: убрать лишний `DISTINCT` (условие через `EXISTS`)
- [x] `uuidV7()` → `UUID.ofEpochMillis` из JDK 26 вместо самописной сборки битов
- [x] `asClientError()` — guard-условия в `when` (Kotlin 2.2)
- [x] gRPC `createVote`: убрать проверку пустого title в транспорте — ошибка приходит ответом с `error_code`, как остальные
- [x] (решить) `Vote.removeParticipant` позволяет создателю удалить себя из участников; удаление несуществующего участника/опции — молчаливый no-op
  - Решение 2026-10-27: так задумано — создатель может провести голосование для группы, не участвуя сам; остаётся создателем и видит голосование. Описано в KDoc, тест в `VoteTest`. Удаление несуществующего — no-op (идемпотентный DELETE)

Value classes до конца:
- [x] `account`: `AuthenticatedUser`, `UserProfile`, `UserQueryService`, `AccountDeleted` на `UserId`/`Email`; убрать мосты `.userId`/`.mailbox`
- [x] `notification`: `EmailBudget`, `EmailPreferences`, `NotificationOutbox.cancelFor` и порты на `UserId`/`Email`
- [x] `isValidEmail`/`canonicalEmail`/`normalizeEmail` — приватные для `Email`
  - Сделано 2026-10-27: заодно `User` и порты `account` (`UserRepository`, `IdentityAdmin`, `DeletedAccountRepository`, `TelegramLinkTokenRepository`) на `UserId`/`Email`; распаковка только в JPA/JDBC-адаптерах, web/gRPC и payload'ах. `EmailValidation.kt` удалён, проверка формата — в `Email.of`

Свежие API:
- [x] gRPC: protobuf Kotlin DSL вместо Java-билдеров (`grpc/`) — остались только билдеры `com.google.rpc.Status`/`ErrorInfo` в `GrpcErrors` (для common protos DSL не генерируется)
- [x] `EmailService`: `@ConcurrencyLimit` вместо `Semaphore` (проверить тестом порядок с `@Retryable`), `@Retryable` на уровне класса
  - Оба пост-процессора ставят advice первым с одинаковым order → на одном бине лимит оборачивал повторы (проверено мутацией: `SmtpConcurrencyLimitTest` падает). Поэтому лимит — на отдельном бине `SmtpTransport` (одна попытка), `@Retryable` — на классе `EmailService`
- [x] `JdbcClient` вместо `JdbcTemplate` в адаптерах `notification` — именованные параметры, общий `claimed(job)` для ограждения по `claim_id`
- [ ] `KeycloakAdminClient`: HTTP interface client (`@HttpExchange`) + `OAuth2ClientHttpRequestInterceptor` вместо ручного кэша токена (или хотя бы `RestClient.Builder` от Boot)
- [x] `SecurityConfig` на Kotlin DSL; убрать неиспользуемый `@EnableMethodSecurity`
- [x] ~~`SecretsValidator` через типизированные свойства вместо `@Value`~~ — не делаем: `KeycloakProperties` во внутреннем пакете `account.keycloak`, `platform` не должен от него зависеть (границы Modulith); `@Value` держит валидатор независимым от модулей
- [x] `NotificationWorker.decode`: `readValue<T>()` из jackson-module-kotlin
- [x] `ensureValid`/`ensureState` с contracts (smart cast после проверки) — `EnsureTest` компилируется только с контрактом
- [ ] Gradle: `jvm-test-suite` для `integrationTest` вместо ручного source set; убрать `-Xjsr305=strict`, если ничего не сломает
- [ ] (косметика) multi-dollar строки в `@Value`/`@Scheduled`

## Проверка в конце
- [x] `./gradlew spotlessCheck detekt check` (покрытие ≥ 95/85)
- [x] `make openapi` — спека и клиенты без изменений (или изменения закоммичены) — `OpenApiSpecIntegrationTest` зелёный, спека не менялась
- [x] Обновить CLAUDE.md, где описаны затронутые соглашения
