# Keycloak with the app's login theme `fate` (frontend/src/login/, built with Keycloakify into a
# provider jar). Build context: frontend/ — `docker build -f infra/keycloak/Dockerfile frontend`.

FROM node:26-alpine AS theme

# keycloakify packs the jar with Maven
RUN apk add --no-cache maven openjdk21-jdk

WORKDIR /app

COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .
RUN --mount=type=cache,target=/root/.m2 npm run build-keycloak-theme

# ─────────────────────────────────────────────────────────────────────────────

FROM quay.io/keycloak/keycloak:26.8.0

COPY --from=theme /app/dist_keycloak/fate-keycloak-theme.jar /opt/keycloak/providers/
