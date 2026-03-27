FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradle/           gradle/
COPY gradlew           gradlew
COPY build.gradle.kts  build.gradle.kts
COPY settings.gradle.kts settings.gradle.kts

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

COPY src/ src/
COPY ../proto/ ../proto/

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
