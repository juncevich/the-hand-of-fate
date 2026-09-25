# Dependency Updates

## 2026-09-26 — Java 26, detekt 2

### Java toolchain `21 → 26` (Temurin)
- Newest release both Kotlin 2.4.20 (max `JvmTarget.JVM_26`) and Spring Boot 4.1 ("compatible up to and including Java 26") support; Java 27 is blocked by both. Note: 26 is a non-LTS release and stopped receiving updates when 27 shipped (last patch 26.0.2.1, Aug 2026) — revisit when Kotlin/Boot support 27, or fall back to 25 LTS
- `org.gradle.java.home` (a machine-specific `/Users/...` path, broken on CI and for other developers) removed from `gradle.properties`; the Gradle JVM is now pinned portably via Daemon JVM criteria (`gradle/gradle-daemon-jvm.properties`: Temurin 26, auto-provisioned through foojay)

### io.gitlab.arturbosch.detekt `1.23.8` → dev.detekt `2.0.0-alpha.6`
- 1.23.8 is the latest stable, but it embeds Kotlin 2.0.21 whose IntelliJ runtime can't parse the version of a Java 25+ JVM and it runs inside the Gradle daemon — so it could not run with a Java 26 daemon (`IllegalArgumentException: 26.0.2.1` / `25.0.3`). 2.0 is still alpha (latest `2.0.0-alpha.6`, 2026-08-04, built against Kotlin 2.4.10); it runs fine on Java 26
- The detekt classpath is pinned to Kotlin 2.4.10 (detekt refuses to run with a different Kotlin than it was compiled with)
- `detekt.yml` migrated to 2.x property names; limits are unchanged — `threshold` (first reported value) → `allowed*` (maximum allowed), mapped using both versions' generated default configs (e.g. `LongParameterList.functionThreshold: 8` → `allowedFunctionParameters: 7`); `build.maxIssues` removed (2.x fails on any issue)
- `./gradlew detekt` now runs the full-analysis (type-resolution) tasks `detektMain`/`detektTest`/`detektIntegrationTest` — in 2.x rules such as `LongParameterList` only run in that mode. Generated protobuf/gRPC sources are excluded. Baselines are per source set (`detekt-baseline-<sourceSet>.xml`): the 3 entries of the old baseline are carried over, plus 38 findings from checks 1.x never ran here (`UseOrEmpty` ×14, `VarCouldBeVal` ×12, `ForbiddenVoid`, `UnsafeCallOnNullableType`, `AbstractClassCanBeInterface`, `InjectDispatcher`, …) — worth fixing separately
- Verified: a probe file with an empty function + 8-parameter function yields exactly the same two findings as 1.23.8 did
- Follow-up (same day): the 38 new findings were fixed in code rather than baselined; only the 3 entries carried over from 1.x remain. Test classes moved from `@Autowired lateinit var` field injection to constructor / `@BeforeEach`-parameter injection, which made `com.ninja-squad:springmockk` (`@MockkBean`) unused — removed

### Docker image
- Build stage `eclipse-temurin:21-jdk-alpine` → `eclipse-temurin:26-jdk` (glibc: the protoc / protoc-gen-grpc-java binaries fetched by the protobuf plugin don't run on musl — the Alpine build stage failed at `generateProto` already before this change), runtime `26-jre-alpine`
- Dropped obsolete JVM flags: `-XX:+UseContainerSupport` (default since JDK 10) and `-Djava.security.egd=file:/dev/./urandom`

Verified: `./gradlew clean check` (123 unit + 77 integration tests, run on Temurin 26.0.2.1; class files are version 70), the Docker image starts on Java 26.0.2 and serves requests, all four `simulation` end-to-end scenarios pass against the jar on Java 26.

## 2026-09-25 — Jackson 3 Kotlin module, Java toolchain

### com.fasterxml.jackson.module:jackson-module-kotlin `2.21.5` → tools.jackson.module:jackson-module-kotlin `3.1.5`
- Spring Boot 4 serializes HTTP JSON with Jackson 3 (`tools.jackson.core:jackson-databind`), so the Jackson 2 Kotlin module on the classpath was never registered with the app's `JsonMapper` — Kotlin default parameter values were ignored on request bodies. Swapped to the Jackson 3 artifact (version managed by the Spring Boot BOM); Jackson 2 support is deprecated in Boot 4.0 and slated for removal in 4.3
- `AbstractApiIntegrationTest` now autowires the application's `JsonMapper` instead of building a Jackson 2 `ObjectMapper` by hand

### Java toolchain `23 → 21`
- The toolchain compiled class files for Java 23 (major version 67) while the Docker image (`temurin:21-jre`), the server (`temurin-21-jdk`) and CI all run Java 21, so the jar could not start there (`UnsupportedClassVersionError`). Aligned the toolchain with the runtime; Java 23 is also a non-LTS release that is out of support

Verified with `./gradlew clean check` (spotless, detekt, 114 unit tests, 59 integration tests — all pass) and by checking the compiled class-file version (65 = Java 21).

### micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp → org.springframework.boot:spring-boot-starter-opentelemetry `4.1.1`
- Boot 4's supported way to wire OpenTelemetry; the starter brings `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp` and `micrometer-registry-otlp` (versions from the Boot BOM)
- `micrometer-registry-otlp` was missing before, so the `management.otlp.metrics.export.*` config was inert and no metrics reached the OTel Collector → Mimir pipeline
- `management.otlp.tracing.endpoint` is deprecated with `level: error` since Boot 4.0 (no longer bound), so traces were going to the SDK default `localhost:4318` regardless of `OTEL_EXPORTER_OTLP_ENDPOINT`. Renamed to `management.opentelemetry.tracing.export.otlp.endpoint`
- The integration-test profile disables OTLP metrics/trace export (no collector there)

Verified with `./gradlew check` (all pass) and by running the boot jar on JDK 21.0.9 against a stub OTLP HTTP receiver: it received `POST /v1/metrics` every step and `POST /v1/traces` after a request.

### net.devh:grpc-server-spring-boot-starter `3.1.0.RELEASE` → org.springframework.boot:spring-boot-starter-grpc-server `4.1.1`
- The third-party starter is no longer actively developed; Spring Boot 4.1 ships first-party gRPC server support (Spring gRPC 1.1.1). Server transport moves from `grpc-netty-shaded` to `grpc-netty`
- Annotations: `net.devh…GrpcService` → `org.springframework.grpc.server.service.GrpcService`, `@GrpcGlobalServerInterceptor` → `@GlobalServerInterceptor`; the `GrpcServerSecurityAutoConfiguration` exclusion is gone
- Config: `grpc.server.{port,address}` → `spring.grpc.server.{port,address}` (`GRPC_BIND_ADDRESS` still applies); reflection and health services are explicitly disabled to keep the same public surface. The custom `grpc.shared-secret` property is unchanged
- Replaced the `resolutionStrategy.force(...)` list and the protobuf `dependencyManagement` override with the Boot BOM's own `grpc-java.version` / `protobuf-java.version` properties. The force list had not been winning over the BOM (grpc-api/grpc-core resolved to 1.83.1 alongside 1.84.0 artifacts); now every `io.grpc` artifact resolves to 1.84.0 and protobuf to 4.36.2

Verified with the new `GrpcServerIntegrationTest` (real TCP server: missing/wrong shared secret → `UNAUTHENTICATED`, valid secret reaches the service, link → create → draw → list flow), written and passing against the old starter first, then passing unchanged after the migration; full `./gradlew check` passes (114 unit, 63 integration tests).

### io.jsonwebtoken:jjwt-api/jjwt-impl/jjwt-jackson `0.13.0` → removed; org.springframework.boot:spring-boot-starter-security-oauth2-resource-server `4.1.1` added
- Access tokens are now issued with Spring Security's `NimbusJwtEncoder` and validated by the built-in OAuth2 resource server (`NimbusJwtDecoder` + `JwtValidators.createDefault()` + a claims validator requiring a UUID `sub` and an `email`), replacing the hand-written `JwtAuthFilter`
- The signing algorithm is pinned to HS256 for both issuing and validation (jjwt picked HS256/384/512 from the key length — the dev/test secrets produced HS384). Access tokens issued before the deploy fail validation once; the SPA's 401 interceptor transparently refreshes them via the unaffected opaque refresh token
- A stale bearer token is still ignored on the public `/api/v1/auth/{login,register,refresh,logout}` endpoints; `/api/v1/auth/logout-all` now requires authentication explicitly
- Boot's gRPC starter auto-enables JWT auth on the gRPC server once the resource server is present; `GrpcServerSecurityAutoConfiguration` / `GrpcServerOAuth2ResourceServerAutoConfiguration` are excluded because the bot authenticates with the shared secret only (caught by `GrpcServerIntegrationTest`)

Verified with the new `JwtAuthenticationIntegrationTest` (9 cases: hand-assembled HS256 tokens — valid, expired, foreign key, `alg:none`, missing `email`, non-UUID `sub`, garbage, identity-from-claims, stale token on logout), written and passing against jjwt first, then passing unchanged after the migration; full `./gradlew check` passes (116 unit, 76 integration tests).

## 2026-09-25

Re-audited every explicit version in `build.gradle.kts` and the Gradle wrapper against Maven Central `maven-metadata.xml` (`<release>` field) and the Gradle Plugin Portal, per the user's request to check and update, majors explicitly in scope. No majors were available for any backend dependency this pass either.

### com.google.protobuf:protobuf-java/protobuf-kotlin/protoc (`protobufVersion`) `4.36.1 → 4.36.2`
- `protobuf-java`/`protobuf-kotlin` cleanly report `<release>4.36.2</release>`; `protoc` bumped in lockstep as usual

### Gradle wrapper `9.7.0 → 9.8.0`
- Confirmed via `services.gradle.org/versions/current`; applied to both `backend` and `perf` wrappers in the same pass

Verified with `./gradlew clean compileKotlin compileTestKotlin` (a clean build was required — the protobuf bump invalidated cached generated proto sources, causing a stale `Unresolved reference 'FateProto'` on an incremental `compileTestKotlin`), `./gradlew test`, `./gradlew detekt`, and `./gradlew spotlessCheck` — all pass with no source changes.

### org.testcontainers:testcontainers-postgresql / testcontainers-junit-jupiter — reconciled, no change
- Double-checked these coordinates directly against `maven-metadata.xml` after a flag that they looked like a possible artifact-id typo (the legacy 1.x line publishes as `org.testcontainers:postgresql` / `org.testcontainers:junit-jupiter`, currently at `1.21.4`). Confirmed `testcontainers-postgresql` / `testcontainers-junit-jupiter` are the real, separate 2.x-line artifact IDs (Testcontainers renamed its module coordinates for the 2.x major), both cleanly reporting `<release>2.0.5</release>` — already latest, no typo, no change needed

Everything else confirmed already at the latest stable release (no change): `kotlin(jvm/plugin.spring/plugin.jpa)` `2.4.20`, `org.springframework.boot` `4.1.1` (4.2.0-M1 is a milestone, intentionally skipped), `io.spring.dependency-management` `1.1.7`, `com.google.protobuf` Gradle plugin `0.10.0`, `com.diffplug.spotless` `8.10.2`, `io.gitlab.arturbosch.detekt` `1.23.8`, `io.grpc:*` (`grpcVersion`) `1.84.0`, `io.grpc:grpc-kotlin-stub`/`protoc-gen-grpc-kotlin` `1.5.0`, `org.springframework.modulith:*` `2.1.1` (2.2.0-M1 is a milestone, intentionally skipped), `net.devh:grpc-server-spring-boot-starter` `3.1.0.RELEASE`, `org.postgresql:postgresql` `42.7.13`, `org.springdoc:springdoc-openapi-starter-webmvc-ui` `3.1.1`, `io.mockk:mockk` `1.14.11`, `com.ninja-squad:springmockk` `5.0.1`, ktlint `1.8.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-*` `1.11.0`, `io.jsonwebtoken:jjwt-*` `0.13.0`.

## 2026-09-15

Re-audited every explicit version in `build.gradle.kts` against Maven Central `maven-metadata.xml` (`<release>` field) and the Gradle Plugin Portal, per the user's request to check and update, majors explicitly in scope. No majors were actually available for any backend dependency — every bump below is a minor/patch release.

### kotlin(jvm/plugin.spring/plugin.jpa) `2.4.0 → 2.4.20`
- Confirmed via the Gradle Plugin Portal `org.jetbrains.kotlin.jvm` marker `<release>` metadata

### org.springframework.boot `4.1.0 → 4.1.1`
- The plugin's `<release>` tag reports `4.2.0-M1`, a milestone — intentionally skipped. `4.1.1` is the latest true stable release on the 4.1.x line

### com.diffplug.spotless (Gradle plugin) `8.9.0 → 8.10.2`
- Confirmed via Gradle Plugin Portal metadata

### io.grpc (`grpcVersion`) `1.83.1 → 1.84.0`
- Confirmed via `io/grpc/grpc-core/maven-metadata.xml` `<release>`, cross-checked in lockstep with `grpc-api`, `grpc-netty-shaded`, `grpc-protobuf`, `grpc-stub`, `protoc-gen-grpc-java` — all publish `1.84.0`

### com.google.protobuf:protobuf-java/protobuf-kotlin/protoc (`protobufVersion`) `4.35.1 → 4.36.1`
- `protobuf-java`/`protobuf-kotlin` cleanly report `<release>4.36.1</release>`. `protoc`'s own metadata has a corrupted `<release>` tag (`21.0-rc-1`, an old out-of-scheme leftover) — cross-verified against the lockstep siblings instead, since `protoc` always ships in lockstep with `protobuf-java`

### org.springframework.modulith:* (bom, starter-core, actuator, starter-test) `2.1.0 → 2.1.1`
- The family's `<release>` tag reports `2.2.0-M1`, a milestone — intentionally skipped. `2.1.1` is the latest true stable release on the 2.1.x line. Lockstep family, bumped together

### org.springdoc:springdoc-openapi-starter-webmvc-ui `3.1.0 → 3.1.1`
- Routine patch bump per Maven Central `<release>` metadata

Verified with `./gradlew compileKotlin compileTestKotlin`, `./gradlew test`, `./gradlew detekt`, and `./gradlew spotlessCheck` — all pass with no source changes.

Everything else confirmed already at the latest stable release (no change): `io.spring.dependency-management` `1.1.7`, `com.google.protobuf` Gradle plugin `0.10.0`, `io.gitlab.arturbosch.detekt` `1.23.8` (no stable Kotlin-2.x-native detekt exists yet — only a prerelease `2.0.0-alpha.6` — the Kotlin-2.0.21 classpath pin for detekt's own runtime remains necessary), `io.grpc:grpc-kotlin-stub`/`protoc-gen-grpc-kotlin` `1.5.0` (Maven Central's `<release>` again reports a stray non-semver git-hash publish, not a real release), `io.jsonwebtoken:jjwt-*` `0.13.0`, `kotlinx-coroutines-*` `1.11.0`, `net.devh:grpc-server-spring-boot-starter` `3.1.0.RELEASE`, `org.postgresql:postgresql` `42.7.13`, `org.testcontainers:*` `2.0.5`, `io.mockk:mockk` `1.14.11`, `com.ninja-squad:springmockk` `5.0.1`, ktlint (`com.pinterest.ktlint:ktlint-cli`) `1.8.0`.

## 2026-08-06

Re-audited every explicit version in `build.gradle.kts` and the Gradle wrapper against Maven Central `maven-metadata.xml` (`<release>` field) and the Gradle Plugin Portal, per the user's request to check and update.

### Gradle wrapper `9.6.1 → 9.7.0`
- Applied via `./gradlew wrapper --gradle-version 9.7.0` (also updated `perf/gradle/wrapper/gradle-wrapper.properties` to match, in the same pass)

### org.springdoc:springdoc-openapi-starter-webmvc-ui `3.0.3 → 3.1.0`
- Routine minor bump per Maven Central `<release>` metadata; no changelog specifics verified

Verified with `./gradlew compileKotlin compileTestKotlin`, `./gradlew test`, `./gradlew detekt`, and `./gradlew spotlessCheck` — all pass with no code changes.

Everything else confirmed already at the latest stable release (no change): Kotlin `2.4.0` (`2.4.20-Beta2` is pre-release only), Spring Boot `4.1.0`, `io.grpc` `1.83.1`, `com.google.protobuf` Gradle plugin `0.10.0`, `com.diffplug.spotless` Gradle plugin `8.9.0`, detekt `1.23.8`, `com.google.protobuf:protobuf-java`/`protobuf-kotlin` `4.35.1` (`4.36.0-RC2` is a release candidate, intentionally skipped), `grpc-kotlin-stub` `1.5.0` (Maven Central's `<release>` again reports a stray non-semver git-hash publish, not a real release), `jjwt` `0.13.0`, `kotlinx-coroutines` `1.11.0`, `spring-modulith-bom` `2.1.0`, `net.devh:grpc-server-spring-boot-starter` `3.1.0.RELEASE`, `io.spring.dependency-management` `1.1.7`, `testcontainers` `2.0.5`, `mockk` `1.14.11`, `springmockk` `5.0.1`, `org.postgresql:postgresql` `42.7.13`, ktlint (`com.pinterest.ktlint:ktlint-cli`) `1.8.0`.

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
