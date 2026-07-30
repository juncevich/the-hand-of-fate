# Build context is the repository ROOT (so the shared proto tree is reachable).
# docker-compose sets `context: .` + `dockerfile: backend/Dockerfile`.
FROM eclipse-temurin:21-jdk-alpine AS build

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

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S fate && adduser -S fate -G fate
USER fate

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080 9090

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseContainerSupport", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
