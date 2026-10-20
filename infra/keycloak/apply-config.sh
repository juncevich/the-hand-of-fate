#!/bin/sh
# Entrypoint of the keycloak-config-cli container: enables a social login provider only when its
# client id is configured (OAUTH_<P>_CLIENT_ID → OAUTH_<P>_ENABLED, read by realm/fate.yaml),
# then applies the realm files listed in IMPORT_FILES_LOCATIONS.
set -eu

enabled() { if [ -n "$1" ]; then echo true; else echo false; fi; }
OAUTH_GOOGLE_ENABLED="$(enabled "${OAUTH_GOOGLE_CLIENT_ID:-}")"
OAUTH_GITHUB_ENABLED="$(enabled "${OAUTH_GITHUB_CLIENT_ID:-}")"
OAUTH_YANDEX_ENABLED="$(enabled "${OAUTH_YANDEX_CLIENT_ID:-}")"
export OAUTH_GOOGLE_ENABLED OAUTH_GITHUB_ENABLED OAUTH_YANDEX_ENABLED

# shellcheck disable=SC2086  # JAVA_OPTS is a list of options
exec java ${JAVA_OPTS:-} -jar /app/keycloak-config-cli.jar
