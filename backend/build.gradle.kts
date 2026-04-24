import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm")                         version "2.3.21"
    kotlin("plugin.spring")               version "2.3.21"
    kotlin("plugin.jpa")                  version "2.3.21"
    id("org.springframework.boot")        version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf")             version "0.10.0"
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

val grpcVersion        = "1.80.0"
val grpcKotlinVersion  = "1.5.0"
val protobufVersion    = "4.34.1"
val jjwtVersion        = "0.13.0"

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")

    // ── Database ─────────────────────────────────────────────────────────────
    runtimeOnly("org.postgresql:postgresql:42.7.10")
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

tasks.withType<Test> {
    useJUnitPlatform()
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
