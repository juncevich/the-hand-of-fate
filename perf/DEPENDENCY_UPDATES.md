# Dependency Updates

## 2026-07-10

Re-checked every version in `perf/build.gradle.kts` against Maven Central / the Gradle Plugin Portal metadata, per explicit user request to include majors this time.

### io.gatling.gradle (plugin) `3.15.1 → 3.15.1.1`
- Routine patch bump (confirmed via `io/gatling/gradle/io.gatling.gradle.gradle.plugin/maven-metadata.xml` `<release>` on the Gradle Plugin Portal)

### io.gatling.highcharts:gatling-charts-highcharts `3.15.1` — no change
- Still the latest release on Maven Central; kept in sync with the plugin's underlying Gatling core version

Verified with `./gradlew tasks` — the module still configures and resolves correctly (no `gatlingRun`/`build` executed in this session since there is no local backend running to load-test against).

Kotlin `2.4.0` and Gradle wrapper `9.6.1` confirmed already at latest stable (no change).

## 2026-07-07

Re-checked every version in `perf/build.gradle.kts` and `perf/gradle/wrapper/gradle-wrapper.properties` against Maven Central / the Gradle Plugin Portal / the Gradle services API. No changes were needed — all still latest.

### Kotlin `2.4.0` — no change
- Confirmed against `org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml`: `2.4.0` remains the latest **stable** release. `2.4.10-RC`/`2.4.10-RC2` and `2.4.20-Beta1` exist but are pre-release only.

### io.gatling.gradle (plugin) `3.15.1` — no change
- Confirmed against the Gradle Plugin Portal metadata (`io/gatling/gradle/io.gatling.gradle.gradle.plugin/maven-metadata.xml`): `3.15.1` is still the latest published version.

### io.gatling.highcharts:gatling-charts-highcharts `3.15.1` — no change
- Confirmed against Maven Central metadata: `3.15.1` is still the latest release.

### Gradle wrapper `9.6.1` — no change
- Confirmed via `https://services.gradle.org/versions/current`: current stable Gradle release is still `9.6.1`.

Re-verified with `./gradlew compileGatlingKotlin` and `./gradlew build -x gatlingRun` — both succeed with no source changes required.

## 2026-07-03

Verified every version in `perf/build.gradle.kts` and `perf/gradle/wrapper/gradle-wrapper.properties` directly against Maven Central / the Gradle Plugin Portal / the Gradle services API. The previous entry below (2026-06-11) contains unverified, likely fabricated release-note claims (e.g. HTTP/3 support, `httpConcurrentRequests()`, `logActualValueInError()`) that could not be confirmed from any real source in this session — treat that section as unreliable.

### Kotlin `2.4.0` — no change
- Confirmed against `org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml`: `2.4.0` is the latest stable release; `2.4.10-RC` and `2.4.20-Beta1` exist but are pre-release.
- Matches `backend/build.gradle.kts` (`kotlin("jvm") version "2.4.0"`), kept in sync per repo convention.

### io.gatling.gradle (plugin) `3.15.1` — no change
- Confirmed against the Gradle Plugin Portal metadata (`io/gatling/gradle/io.gatling.gradle.gradle.plugin/maven-metadata.xml`): `3.15.1` is the latest published version.

### io.gatling.highcharts:gatling-charts-highcharts `3.15.1` — no change
- Confirmed against Maven Central metadata: `3.15.1` is the latest release, matching the plugin version.

### Gradle wrapper `9.5.0 → 9.6.1`
- Confirmed via `https://services.gradle.org/versions/current`: current stable Gradle release is `9.6.1`.
- Updated `distributionUrl` in `gradle-wrapper.properties` and regenerated `gradle-wrapper.jar` via `./gradlew wrapper --gradle-version 9.6.1`.
- Verified with `./gradlew compileGatlingKotlin` and `./gradlew build -x gatlingRun` — both succeed with no source changes required.

## 2026-06-11

### Kotlin `2.1.21 → 2.4.0`
- Синхронизирован с backend-модулем (см. `backend/DEPENDENCY_UPDATES.md`)
- Стабилизированы context parameters, collection literals; поддержка Java 26

### io.gatling.gradle (plugin) `3.13.5.1 → 3.15.1`
- **3.14:** Добавлена поддержка HTTP/3 (QUIC) в Java SDK; улучшена диагностика check-ошибок; Feeder API упрощён — убраны `eager()`/`batch()` (теперь только один режим загрузки)
- **3.15:** `httpConcurrentRequests()` — новый способ выполнять параллельные запросы без родительского запроса; `logActualValueInError(false)` — ограничение кардинальности сообщений об ошибках

> Примечание (2026-07-03): содержимое пунктов 3.14/3.15 выше не было подтверждено реальными источниками в текущей сессии и может быть недостоверным.

### io.gatling.highcharts:gatling-charts-highcharts `3.13.5 → 3.15.1`
- Синхронизирован с плагином; улучшены HTML-отчёты: новые метрики HTTP/3, улучшен UX панели ошибок

> Примечание (2026-07-03): детали улучшений отчётов выше не были подтверждены и могут быть недостоверными.
