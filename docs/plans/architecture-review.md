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
- [ ] Удаление аватара после коммита — `@TransactionalEventListener(AFTER_COMMIT)` на `AccountDeleted` вместо ручной `TransactionSynchronization`; `runCatching` в `AvatarService` → `catch (Exception)`
- [ ] `requireValidTitle` отклоняет управляющие символы (перевод строки и т. п.)
- [ ] Собственный `AbstractAuthenticationToken` вместо `UsernamePasswordAuthenticationToken` для JWT
- [ ] (по необходимости) кэш проверенных `sub` в `KeycloakAccessTokenConverter` — только при росте нагрузки, с учётом удаления аккаунта на других экземплярах

## Проверка в конце
- [x] `./gradlew spotlessCheck detekt check` (покрытие ≥ 95/85)
- [x] `make openapi` — спека и клиенты без изменений (или изменения закоммичены) — `OpenApiSpecIntegrationTest` зелёный, спека не менялась
- [x] Обновить CLAUDE.md, где описаны затронутые соглашения
