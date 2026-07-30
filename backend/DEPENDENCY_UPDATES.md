# Dependency Updates

## 2026-07-30

Re-audited every explicit version in `build.gradle.kts` and the Gradle wrapper against Maven Central `maven-metadata.xml` (`<release>` field) and the Gradle Plugin Portal, per the user's request to check and update, majors in scope too. Only two patch/minor bumps were available.

### io.grpc (`grpcVersion`) `1.82.2 → 1.83.1`
- Confirmed via `io/grpc/grpc-core/maven-metadata.xml` `<release>` (and cross-checked `grpc-stub`, `grpc-protobuf`, `grpc-netty-shaded`, `protoc-gen-grpc-java` — all publish in lockstep at `1.83.1`)

### com.diffplug.spotless (Gradle plugin) `8.8.0 → 8.9.0`
- Routine minor bump per Gradle Plugin Portal metadata; no changelog specifics verified

Verified with `./gradlew compileKotlin compileTestKotlin`, `./gradlew test`, `./gradlew detekt`, and `./gradlew spotlessCheck` — all pass with no code changes.

Everything else confirmed already at the latest stable release (no change): Gradle wrapper `9.6.1`, Kotlin `2.4.0` (`2.4.20-Beta2` is pre-release only), Spring Boot `4.1.0`, `com.google.protobuf` Gradle plugin `0.10.0`, detekt `1.23.8`, `com.google.protobuf:protobuf-java`/`protobuf-kotlin` `4.35.1` (`4.36.0-RC1` is a release candidate, intentionally skipped), `grpc-kotlin-stub` `1.5.0` (Maven Central's `<release>` again reports a stray non-semver git-hash publish, not a real release — see the 2026-07-07 note below), `jjwt` `0.13.0`, `kotlinx-coroutines` `1.11.0`, `spring-modulith-bom` `2.1.0`, `net.devh:grpc-server-spring-boot-starter` `3.1.0.RELEASE`, `springdoc-openapi-starter-webmvc-ui` `3.0.3`, `io.spring.dependency-management` `1.1.7`, `testcontainers` `2.0.5`, `mockk` `1.14.11`, `springmockk` `5.0.1`, `org.postgresql:postgresql` `42.7.13`, ktlint (`com.pinterest.ktlint:ktlint-cli`) `1.8.0`.

## 2026-07-10

Re-audited every explicit version in `build.gradle.kts` against Maven Central `maven-metadata.xml` (`<release>` field, not the lagging `search.maven.org` Solr index — cross-checked and confirmed the Solr index under-reports several packages already at their true latest, e.g. `springdoc-openapi-starter-webmvc-ui`, `mockk`, `springmockk`, `spotless-plugin-gradle`, `kotlinx-coroutines-core`, `protobuf-java`, which the XML metadata confirmed were already current). Per explicit user request, majors were in scope too — none were available/compatible.

### io.grpc (`grpcVersion`) `1.82.1 → 1.82.2`
- Routine patch bump (`grpc-core` `<release>` on Maven Central)

Verified with `./gradlew compileKotlin compileTestKotlin` and `./gradlew test` — both pass with no code changes.

Everything else confirmed already at the latest stable release (no change): Gradle wrapper `9.6.1`, Kotlin `2.4.0` (newer `2.4.20-Beta1` is pre-release only), Spring Boot `4.1.0`, `com.google.protobuf` Gradle plugin `0.10.0`, `com.diffplug.spotless` Gradle plugin `8.8.0`, detekt `1.23.8`, `com.google.protobuf:protobuf-java`/`protobuf-kotlin` `4.35.1` (newer `4.36.0-RC1` is a release candidate, intentionally skipped), `grpc-kotlin-stub` `1.5.0`, `jjwt` `0.13.0`, `kotlinx-coroutines` `1.11.0`, `spring-modulith-bom` `2.1.0`, `net.devh:grpc-server-spring-boot-starter` `3.1.0.RELEASE`, `springdoc-openapi-starter-webmvc-ui` `3.0.3`, `testcontainers` `2.0.5`, `mockk` `1.14.11`, `springmockk` `5.0.1`, `org.postgresql:postgresql` `42.7.13`, ktlint `1.8.0`.

## 2026-07-07

Re-audited every explicit version in `build.gradle.kts` and the Gradle wrapper against Maven Central / Gradle Plugin Portal `maven-metadata.xml`, per the user's request to check and update, including majors. Almost everything was already at the latest stable release (four days after the previous audit); only one patch bump was available:

### org.postgresql:postgresql `42.7.12 → 42.7.13`
- Released 2026-07-06. Notable changes from the pgjdbc changelog:
  - `reWriteBatchedInserts` now merges up to 32768 rows into one multi-values `INSERT` (bounded by the 65535 bind-parameter limit), instead of capping at 128; new `reWriteBatchedInsertsSize` property lowers the cap if needed
  - Prepared-statement cache is now invalidated after CREATE/DROP/ALTER (new `flushCacheOnDdl` property, default `true`) and after a `search_path` change reported via GUC_REPORT (PostgreSQL 18+)
  - `PGXAConnection` no longer saves/restores the underlying connection's `autoCommit` flag around XA-protocol SQL, fixing "2nd phase commit must be issued using an idle connection" failures during recovery on managed datasources (TomEE, WildFly, WebSphere Liberty)
  - `PGXAConnection.prepare()` now mutates XA state only after `PREPARE TRANSACTION` succeeds, fixing a `rollback(xid)` mishandling case that Narayana escalated to `HeuristicMixedException`
  - Empty `timestamp`/`timestamptz`/`date` text now raises a clear `SQLException` (`22007`) instead of an `ArrayIndexOutOfBoundsException`
  - Various other fixes: `LargeObject.close()` now flushes buffered writes before closing; `classLoaderStrategy` connection property added for non-flat classpaths (Quarkus, OSGi); FIPS JVM support for building PKIX trust anchors without a `KeyStore`

No compilation or test changes were required — `./gradlew build -x test`, `./gradlew test`, `./gradlew detekt spotlessCheck` all pass unchanged.

Everything else was verified as already at the latest stable release and left unchanged: Gradle wrapper `9.6.1` (confirmed current via `services.gradle.org/versions/current`), Kotlin `2.4.0` (the only newer entries on the Gradle Plugin Portal are pre-release: `2.4.0-RC2`, `2.4.10-RC`, `2.4.20-Beta1`), Spring Boot `4.1.0`, `io.spring.dependency-management` `1.1.7`, `com.google.protobuf` Gradle plugin `0.10.0`, `com.diffplug.spotless` Gradle plugin `8.8.0`, detekt `1.23.8`, `io.grpc` `1.82.1`, `com.google.protobuf:protobuf-java`/`protobuf-kotlin` `4.35.1`, `jjwt` `0.13.0`, `kotlinx-coroutines` `1.11.0`, `spring-modulith-bom` `2.1.0`, `net.devh:grpc-server-spring-boot-starter` `3.1.0.RELEASE`, `springdoc-openapi-starter-webmvc-ui` `3.0.3`, `testcontainers` `2.0.5`, `mockk` `1.14.11`, `springmockk` `5.0.1`, ktlint `1.8.0`.

Note on `grpc-kotlin-stub`: Maven Central's `maven-metadata.xml` `<latest>`/`<release>` fields report a raw git-commit-hash "version" (`6f774052d1d6923f8af2e0023886d69949b695ee`) published after `1.5.0`. That is not a semver release (no corresponding GitHub release/tag was found) and was treated as a stray/dev publish rather than a real stable version — `grpc-kotlin-stub` was left at `1.5.0`, the newest proper release.

## 2026-07-03

Full audit of every explicit version in `build.gradle.kts` and the Gradle wrapper against Maven Central / Gradle Plugin Portal `maven-metadata.xml` (source of truth, not `search.maven.org`, which lags). Most dependencies were already at the latest stable release; the following had newer stable versions available:

### Gradle wrapper `9.4.1 → 9.6.1`
- Routine version bump (latest stable per `https://services.gradle.org/versions/current`)

### com.diffplug.spotless (Gradle plugin) `8.6.0 → 8.8.0`
- Routine version bump (two patch/minor releases since 8.6.0; no changelog specifics verified)

### io.grpc (`grpcVersion`) `1.82.0 → 1.82.1`
- Routine patch bump

### com.google.protobuf (`protobufVersion`, protobuf-java/protobuf-kotlin/protoc) `4.35.0 → 4.35.1`
- Routine patch bump

### org.postgresql:postgresql `42.7.11 → 42.7.12`
- Routine patch bump

No compilation or test changes were required — `./gradlew build -x test`, `./gradlew test`, `./gradlew detekt spotlessCheck` all pass unchanged.

Everything else declared in `build.gradle.kts` was already at the latest stable release as of this audit and was left unchanged: Kotlin `2.4.0`, Spring Boot `4.1.0`, `io.spring.dependency-management` `1.1.7`, `com.google.protobuf` Gradle plugin `0.10.0`, detekt `1.23.8`, `grpc-kotlin-stub` `1.5.0`, `jjwt` `0.13.0`, `kotlinx-coroutines` `1.11.0`, `spring-modulith-bom` `2.1.0`, `net.devh:grpc-server-spring-boot-starter` `3.1.0.RELEASE`, `springdoc-openapi-starter-webmvc-ui` `3.0.3`, `testcontainers` `2.0.5`, `mockk` `1.14.11`, `springmockk` `5.0.1`, ktlint `1.8.0`.

Note: `backend/gradle.properties` (untracked, machine-local `org.gradle.java.home` pointing at a personal JDK install) and `backend/detekt-baseline.xml` (untracked detekt baseline) were left untouched as instructed — they were absent from this worktree checkout and were copied over from the main working tree only so the build/detekt/test commands above could run; their content was not modified.

## 2026-06-11

### Kotlin `2.3.21 → 2.4.0`
- Стабилизированы context parameters, explicit backing fields и annotation use-site targets
- Экспериментальная поддержка collection literals (`[1, 2, 3]` вместо `listOf(...)`)
- Стабилизирован UUID API в стандартной библиотеке; добавлены функции проверки порядка коллекций
- Kotlin/JVM: поддержка Java 26 и включена по умолчанию запись аннотаций в метаданные классов

### org.springframework.boot `4.0.6 → 4.1.0`
- Добавлена нативная auto-configuration для gRPC серверов и клиентов (актуально, хотя проект использует `net.devh` стартер)
- Клиенты HTTP (`RestClient`, `WebClient`) получили `InetAddressFilter` для защиты от SSRF-атак
- Автоматическая регистрация `RedisMessageListenerContainer` при наличии listener-методов
- Обновлена базовая платформа: Spring Framework 7.1, Micrometer 1.16

### io.grpc:grpc-java `1.81.0 → 1.82.0`
- Исправлен jitter диапазона backoff ретраев до `[0.8, 1.2]` (соответствие gRPC A6)
- Исправлено состояние гонки в `RetriableStream` — счётчик `inFlightSubStreams` мог стать рассогласованным при конкурентных retry/deadline, вызывая зависание вызовов

### org.springframework.modulith:spring-modulith-bom `2.0.1 → 2.1.0`
- `@ModuleSlicing` теперь предпочитает явно объявленные классы с `@SpringBootApplication`
- Улучшена обработка транзакций в интеграции с JobRunr
- Обновлена платформа: Spring Boot 4.1.0, Spring Framework 7.1

### io.mockk:mockk `1.14.9 → 1.14.11`
- `1.14.10`: Исправления совместимости с Kotlin 2.4.0
- `1.14.11`: Параметр `clear = true` в `confirmVerified()` — сбрасывает флаги верификации и записанные вызовы после подтверждения

### com.diffplug.spotless (Gradle plugin) `8.5.0 → 8.6.0`
- Исправлена `predeclareDepsFromBuildscript()` для Gradle 9.x (устранена оставшаяся несовместимость)
- Обновлены встроенные версии инструментов форматирования по умолчанию

## 2026-05-27

### Включены виртуальные потоки Java 21 (Project Loom)
- Добавлено `spring.threads.virtual.enabled: true` в `application.yml`
- Tomcat переключён на `VirtualThreadExecutor` для обработки HTTP-запросов
- `@Async`-задачи (отправка email в `NotificationAdapter`) теперь исполняются на виртуальных потоках
- `Thread.sleep()` в `withRetry()` паркует виртуальный поток вместо блокировки платформенного

## 2026-05-25

### com.google.protobuf:protobuf-kotlin `4.34.1 → 4.35.0`
- Добавлен `enforce_naming_style` enum feature (Edition 2026) — обнаруживает и предотвращает коллизии имён полей в схемах
- Генератор Kotlin/Native теперь использует полностью квалифицированные scalar-типы
- Исправлен JSON-форматтер: убран `toBigIntegerExact()`, вызывавший деградацию производительности при больших экспонентах
- Добавлены вспомогательные функции `BytecodeClassName` в генераторе Java-кода
- Поддержка Bazel 9; поддержка Bazel 7 прекращена (актуально только при сборке protobuf из исходников)

### org.springframework.modulith:spring-modulith-bom `2.0.0 → 2.0.1`
- Исправлена регрессия в `@ApplicationModuleTest` — бины из тестовых конфигураций не поднимались при bootstrap
- Исправлена генерация CGLib-прокси для `JdbcEventPublicationRepositoryV2`, ломавшая компиляцию GraalVM native image
- Исправлен `ClassNotFoundException` при обработке классов `package-info` во время bootstrap
- Добавлена возможность сброса сдвига в `TimeMachine` для более гибкого тестирования времени
- Обновлены транзитивные зависимости: Spring Boot 4.0.1, Spring Framework 7.0.2, jMolecules 2025.0.2, Testcontainers 2.0.3, Micrometer Tracing 1.6.1

### com.diffplug.spotless (Gradle plugin) `8.4.0 → 8.5.0`
- Добавлен формат `toml` с шагом `versionCatalog()` для форматирования и сортировки файлов `libs.versions.toml`
- Scalafmt: версия теперь автоматически читается из конфиг-файла, если не задана явно в плагине
- Исправлена `predeclareDepsFromBuildscript()` — была сломана под Gradle 9
- Исправлена неидемпотентность форматирования при совместном использовании `importOrder()` и `greclipse()`
- Обновлены встроенные версии по умолчанию: Cleanthat `2.24 → 2.25`, Eclipse JDT `4.35 → 4.39`
- Расширение `spotlessPredeclare` теперь видно через type-safe accessors Kotlin DSL без предварительного включения
