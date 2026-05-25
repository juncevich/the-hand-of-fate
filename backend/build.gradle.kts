import com.google.protobuf.gradle.*
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    kotlin("jvm")                              version "2.3.21"
    kotlin("plugin.spring")                    version "2.3.21"
    kotlin("plugin.jpa")                       version "2.3.21"
    id("org.springframework.boot")             version "4.0.6"
    id("io.spring.dependency-management")      version "1.1.7"
    id("com.google.protobuf")                  version "0.10.0"
    id("com.diffplug.spotless")                version "8.5.0"
    id("io.gitlab.arturbosch.detekt")          version "1.23.8"
}

group   = "com.juncevich"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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

val grpcVersion          = "1.81.0"
val grpcKotlinVersion    = "1.5.0"
val protobufVersion      = "4.35.0"
val jjwtVersion          = "0.13.0"
val coroutinesVersion    = "1.11.0"
val modulithVersion      = "2.0.1"

configurations.all {
    resolutionStrategy.force(
        "io.grpc:grpc-core:$grpcVersion",
        "io.grpc:grpc-api:$grpcVersion",
        "io.grpc:grpc-netty-shaded:$grpcVersion",
        "io.grpc:grpc-protobuf:$grpcVersion",
        "io.grpc:grpc-stub:$grpcVersion",
    )
}

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
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // ── JWT ──────────────────────────────────────────────────────────────────
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // ── gRPC ─────────────────────────────────────────────────────────────────
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVersion")

    // ── Database ─────────────────────────────────────────────────────────────
    runtimeOnly("org.postgresql:postgresql:42.7.11")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // ── Observability ─────────────────────────────────────────────────────────
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // ── OpenAPI ───────────────────────────────────────────────────────────────
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    // ── Test ──────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("com.ninja-squad:springmockk:5.0.1")

    // ── Static analysis ───────────────────────────────────────────────────────
    // detekt-formatting is intentionally excluded — spotless/ktlint owns all formatting
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
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

tasks.withType<Test> {
    useJUnitPlatform()
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

spotless {
    kotlin {
        ktlint("1.8.0")
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
    }
    // .kts formatting is skipped: ktlint does not yet support Kotlin 2.3.x script parsing
}

// detekt 1.23.x was compiled with Kotlin 2.0.21; pin its classpath to avoid version mismatch
configurations.matching { it.name.contains("detekt", ignoreCase = true) }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.21")
        }
    }
}

detekt {
    config.setFrom(file("detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom("src/main/kotlin", "src/test/kotlin", "src/integrationTest/kotlin")
    baseline = file("detekt-baseline.xml")
}

// detekt's bundled IntelliJ runtime doesn't handle Java 26+ — run against a Java 17 JDK home
val detektJdkHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
    .map { it.metadata.installationPath }
tasks.withType<Detekt>().configureEach {
    jdkHome.set(detektJdkHome)
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jdkHome.set(detektJdkHome)
}

// Ensure generated sources are on the compile classpath
afterEvaluate {
    val generatedSourcesDir = layout.buildDirectory.dir("generated/sources/proto/main").get().asFile
    sourceSets["main"].java.srcDirs(
        "$generatedSourcesDir/java",
        "$generatedSourcesDir/grpc",
        "$generatedSourcesDir/grpckt",
        "$generatedSourcesDir/kotlin",
    )
}
