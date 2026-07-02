# Dependency Updates

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
