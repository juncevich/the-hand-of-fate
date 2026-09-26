import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm")                              version "2.4.20"
    kotlin("plugin.spring")                    version "2.4.20"
    kotlin("plugin.jpa")                       version "2.4.20"
    id("org.springframework.boot")             version "4.1.1"
    id("io.spring.dependency-management")      version "1.1.7"
    id("com.google.protobuf")                  version "0.10.0"
    id("com.diffplug.spotless")                version "8.10.2"
    id("dev.detekt")                           version "2.0.0-alpha.6"
    jacoco
}

group   = "com.juncevich"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val grpcVersion          = "1.84.0"
val grpcKotlinVersion    = "1.5.0"
val protobufVersion      = "4.36.2"
val coroutinesVersion    = "1.11.0"
val modulithVersion      = "2.1.1"

// The Spring Boot BOM pins older gRPC / protobuf releases; override its version
// properties so the runtime matches the protoc / protoc-gen-grpc-java used for codegen.
extra["grpc-java.version"] = grpcVersion
extra["protobuf-java.version"] = protobufVersion

dependencies {
    // ── Spring Modulith ───────────────────────────────────────────────────────
    implementation(platform("org.springframework.modulith:spring-modulith-bom:$modulithVersion"))
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-actuator")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")

    // ── Spring Boot ─────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    // ── Kotlin ───────────────────────────────────────────────────────────────
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // ── JWT (issued via NimbusJwtEncoder, validated by the resource server) ────
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    // ── gRPC ─────────────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVersion")

    // ── Database ─────────────────────────────────────────────────────────────
    runtimeOnly("org.postgresql:postgresql:42.7.13")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // ── Observability ─────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ── OpenAPI ───────────────────────────────────────────────────────────────
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    // ── Test ──────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("io.mockk:mockk:1.14.11")

    // ── Static analysis ───────────────────────────────────────────────────────
    // detekt-formatting is intentionally excluded — spotless/ktlint owns all formatting
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        if (findByName("grpc") == null) id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        if (findByName("grpckt") == null) id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                if (findByName("grpc") == null) id("grpc")
                if (findByName("grpckt") == null) id("grpckt")
            }
            task.builtins {
                if (findByName("kotlin") == null) id("kotlin")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("${rootProject.projectDir}/../proto")
        }
    }
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    kotlin.srcDir("src/integrationTest/kotlin")
    resources.srcDir("src/integrationTest/resources")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// The integrationTest source set lists src/integrationTest/resources both by
// convention and via the explicit srcDir above, so files there are enumerated
// twice; collapse the duplicates instead of failing the resources task.
tasks.named<ProcessResources>("processIntegrationTestResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// `./gradlew bootRun` is a local-dev entry point: default it to the `dev`
// profile (relaxes the production secret guard, seeds the demo user, disables
// Secure cookies) unless the developer explicitly selects another profile.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Deterministically set the profile on the forked JVM (defaulting to `dev`),
    // rather than relying on the env reaching the fork through the Gradle daemon.
    val explicitProfile =
        System.getProperty("spring.profiles.active") ?: System.getenv("SPRING_PROFILES_ACTIVE")
    jvmArgs("-Dspring.profiles.active=${explicitProfile ?: "dev"}")
    (project.findProperty("jvmArgs") as String?)?.let { extra ->
        jvmArgs(extra.split(" ").filter { it.isNotBlank() })
    }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests (spin up PostgreSQL via TestContainers)"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
}

tasks.check {
    dependsOn(integrationTest)
}

// ── Coverage (JaCoCo) ────────────────────────────────────────────────────────
// `./gradlew jacocoTestReport` merges unit + integration test coverage into
// build/reports/jacoco/test/{html,jacocoTestReport.xml}; `check` produces it too and
// fails via jacocoTestCoverageVerification when coverage drops below the minimums.
jacoco {
    toolVersion = "0.8.15"
}

// Generated protobuf/gRPC classes share the com.juncevich.fate.grpc package with
// hand-written code, so they are excluded by the exact names generateProto emitted:
// `X.java` → X, X$Inner; `XKt.kt` → XKt, XKt$Dsl, XKtKt (file facade).
val generatedProtoClassNames: Provider<Set<String>> =
    layout.buildDirectory.dir("generated/sources/proto/main").map { root ->
        buildSet {
            fileTree(root).visit {
                if (!isDirectory) {
                    // Drop the leading protoc-plugin directory (java/, grpc/, kotlin/, grpckt/)
                    val className = relativePath.segments.drop(1).joinToString("/").substringBeforeLast('.')
                    add(className)
                    add(className + "Kt")
                }
            }
        }
    }

val handWrittenMainClasses =
    sourceSets.main.get().output.classesDirs.asFileTree.matching {
        val generated by lazy { generatedProtoClassNames.get() }
        exclude { element ->
            !element.isDirectory &&
                element.relativePath.pathString.substringBeforeLast(".class").substringBefore('$') in generated
        }
    }

tasks.jacocoTestReport {
    description = "Generates a merged unit + integration test coverage report (generated gRPC code excluded)"
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(handWrittenMainClasses)
}

// Minimums sit a few points below the current coverage (98.9% lines / 88.7% branches,
// 2026-09-26) so a change that ships untested code fails the build; raise them as
// coverage grows rather than lowering them to get a build through.
tasks.jacocoTestCoverageVerification {
    description = "Fails the build when merged unit + integration coverage drops below the minimums"
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    classDirectories.setFrom(handWrittenMainClasses)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.95".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}

spotless {
    kotlin {
        ktlint("1.8.0")
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
    }
    // .kts formatting is skipped: ktlint does not yet support Kotlin 2.3.x script parsing
}

// ── Static analysis (detekt) ─────────────────────────────────────────────────
// detekt 2.x (still alpha): 1.23.x embeds Kotlin 2.0.21, whose IntelliJ runtime can't run in
// a Java 25+ Gradle daemon. The per-source-set tasks (detektMain, detektTest, …) run in full
// analysis mode, which some rules (e.g. LongParameterList) require in 2.x.
// detekt must run with the Kotlin version it was compiled against, not the project's
configurations.matching { it.name.contains("detekt", ignoreCase = true) }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.4.10")
        }
    }
}

detekt {
    config.setFrom(file("detekt.yml"))
    buildUponDefaultConfig = true
    baseline = file("detekt-baseline.xml")
}

// Source-set tasks see generated protobuf/gRPC stubs too; only hand-written code is analysed
tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude { it.file.invariantSeparatorsPath.contains("/build/generated/") }
}
tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    exclude { it.file.invariantSeparatorsPath.contains("/build/generated/") }
}

// `./gradlew detekt` (CI, docs) runs the full-analysis source-set tasks; its own light-mode
// pass would only repeat a subset of their checks. Baselines: detekt-baseline-<sourceSet>.xml,
// regenerate with detektBaselineMain / detektBaselineTest / detektBaselineIntegrationTest.
tasks.named("detekt") {
    enabled = false
    dependsOn("detektMain", "detektTest", "detektIntegrationTest")
}
