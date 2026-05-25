# Dependency Updates

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
