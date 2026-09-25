# Dependency Updates

## 2026-09-26 — Java 26, detekt 2

### Java toolchain `21 → 26` (Temurin)
- Newest release both Kotlin 2.4.20 (max `JvmTarget.JVM_26`) and Spring Boot 4.1 ("compatible up to and including Java 26") support; Java 27 is blocked by both. Note: 26 is a non-LTS release and stopped receiving updates when 27 shipped (last patch 26.0.2.1, Aug 2026) — revisit when Kotlin/Boot support 27, or fall back to 25 LTS
- `org.gradle.java.home` (a machine-specific `/Users/...` path, broken on CI and for other developers) removed from `gradle.properties`; the Gradle JVM is now pinned portably via Daemon JVM criteria (`gradle/gradle-daemon-jvm.properties`: Temurin 26, auto-provisioned through foojay)

- The simulations previously had no toolchain (they compiled with whatever JVM ran Gradle — 17 locally, 21 on CI); now `kotlin { jvmToolchain(26) }`. The Gatling plugin runs simulations in the Gradle daemon JVM (it has no toolchain support), which is why the daemon itself must be Java 26 — with a Java 21 daemon `gatlingRun` failed with `UnsupportedClassVersionError`

### io.gitlab.arturbosch.detekt `1.23.8` → dev.detekt `2.0.0-alpha.6`
- 1.23.8 is the latest stable, but it embeds Kotlin 2.0.21 whose IntelliJ runtime can't parse the version of a Java 25+ JVM and it runs inside the Gradle daemon — so it could not run with a Java 26 daemon (`IllegalArgumentException: 26.0.2.1` / `25.0.3`). 2.0 is still alpha (latest `2.0.0-alpha.6`, 2026-08-04, built against Kotlin 2.4.10); it runs fine on Java 26
- The detekt classpath is pinned to Kotlin 2.4.10 (detekt refuses to run with a different Kotlin than it was compiled with)
- `detekt.yml` migrated to 2.x property names; limits are unchanged — `threshold` (first reported value) → `allowed*` (maximum allowed), mapped using both versions' generated default configs (e.g. `LongParameterList.functionThreshold: 8` → `allowedFunctionParameters: 7`); `build.maxIssues` removed (2.x fails on any issue)
- `./gradlew detekt` runs `detektGatling` (full analysis of the Gatling sources); no findings, no baseline needed

Verified: `spotlessCheck detekt compileGatlingKotlin` pass, and `gatlingRun --simulation simulations.SmokeSimulation` passes on Java 26 against a live backend (3/3 requests OK).

## 2026-09-25

Re-checked every version in `perf/build.gradle.kts` and the Gradle wrapper against Maven Central / the Gradle Plugin Portal metadata, per the user's request to check and update, majors explicitly in scope (same audit pass as `backend`). No majors were available.

### Gradle wrapper `9.7.0 → 9.8.0`
- Same bump applied to `backend` in this pass; confirmed via `services.gradle.org/versions/current`

Verified with `./gradlew compileGatlingKotlin`, `./gradlew detekt`, and `./gradlew spotlessCheck` — all pass with no source changes.

Everything else confirmed already at the latest stable release (no change): `kotlin(jvm)` `2.4.20`, `io.gatling.gradle` `3.15.1.3`, `com.diffplug.spotless` `8.10.2`, `io.gitlab.arturbosch.detekt` `1.23.8`, `io.gatling.highcharts:gatling-charts-highcharts` `3.15.1`.

## 2026-09-15

Re-checked every version in `perf/build.gradle.kts` against Maven Central / the Gradle Plugin Portal metadata, per the user's request to check and update, majors explicitly in scope (same audit pass as `backend`). No majors were available.

### kotlin(jvm) `2.4.0 → 2.4.20`
- Same bump applied to `backend` in this pass; confirmed via the Gradle Plugin Portal `org.jetbrains.kotlin.jvm` marker metadata

### io.gatling.gradle (plugin) `3.15.1.2 → 3.15.1.3`
- Confirmed via the Gradle Plugin Portal listing only — Gatling does not publish this plugin marker to Maven Central (`io/gatling/gradle/io.gatling.gradle.gradle.plugin` 404s), so this bump carries slightly lower confidence than a Maven-metadata-verified one

Verified with `./gradlew compileGatlingKotlin` — compiles cleanly with no source changes.

`io.gatling.highcharts:gatling-charts-highcharts` `3.15.1` confirmed already at latest stable (no change), in sync with the plugin's underlying Gatling core version.

## 2026-08-06

Re-checked every version in `perf/build.gradle.kts` and the Gradle wrapper against Maven Central / the Gradle Plugin Portal metadata, per the user's request to check and update.

### Gradle wrapper `9.6.1 → 9.7.0`
- Applied via `./gradlew wrapper --gradle-version 9.7.0` (same bump applied to `backend` in this pass)

### io.gatling.gradle (plugin) `3.15.1.2` — no change
- Still the latest release on the Gradle Plugin Portal

### io.gatling.highcharts:gatling-charts-highcharts `3.15.1` — no change
- Still the latest release on Maven Central, in sync with the plugin's underlying Gatling core version

Verified with `./gradlew compileGatlingKotlin` — compiles cleanly with no source changes.

Kotlin `2.4.0` (`2.4.20-Beta2` is pre-release only) confirmed already at latest stable (no change).

## 2026-07-30

Re-checked every version in `perf/build.gradle.kts` against Maven Central / the Gradle Plugin Portal metadata, per explicit user request to include majors.

### io.gatling.gradle (plugin) `3.15.1.1 → 3.15.1.2`
- Routine patch bump (confirmed via Gradle Plugin Portal `maven-metadata.xml` `<release>`)

### io.gatling.highcharts:gatling-charts-highcharts `3.15.1` — no change
- Still the latest release on Maven Central, in sync with the plugin's underlying Gatling core version

Verified with `./gradlew compileGatlingKotlin` — compiles cleanly with no source changes.

Kotlin `2.4.0` (`2.4.20-Beta2` is pre-release only) and Gradle wrapper `9.6.1` confirmed already at latest stable (no change).

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
