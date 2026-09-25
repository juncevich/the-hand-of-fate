# Build context is the repository ROOT (so the shared proto tree is reachable).
# docker-compose sets `context: .` + `dockerfile: backend/Dockerfile`.

# Temurin 26 satisfies both gradle/gradle-daemon-jvm.properties and the Java 26 toolchain,
# so Gradle provisions no extra JDK. glibc-based (not Alpine): the protoc / protoc-gen-grpc-java
# binaries fetched by the protobuf plugin are glibc executables and don't run on musl.
FROM eclipse-temurin:26-jdk AS build

WORKDIR /app

COPY backend/gradle/           gradle/
COPY backend/gradlew           gradlew
COPY backend/build.gradle.kts  build.gradle.kts
COPY backend/settings.gradle.kts settings.gradle.kts

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

COPY backend/src/ src/
# build.gradle.kts sources protos from ${rootProject.projectDir}/../proto → /proto
COPY proto/ /proto/

RUN ./gradlew bootJar --no-daemon -x test

# ─────────────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:26-jre-alpine

RUN addgroup -S fate && adduser -S fate -G fate
USER fate

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080 9090

# Container-aware memory sizing is on by default since JDK 10 (UseContainerSupport)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
