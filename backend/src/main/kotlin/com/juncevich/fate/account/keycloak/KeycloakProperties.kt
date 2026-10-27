package com.juncevich.fate.account.keycloak

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Keycloak, the authorization server (docs/adr/0001). Its access tokens are issued under
 * [publicUrl] — the address browsers use — while the backend fetches the signing keys and calls
 * the Admin API at [url], which may be an internal address of the same server.
 */
@ConfigurationProperties(prefix = "app.keycloak")
data class KeycloakProperties(
    /** Base URL the backend reaches Keycloak at, e.g. `http://keycloak:8080/auth`. */
    val url: String,
    /** Base URL browsers reach Keycloak at; tokens carry `iss` = `<publicUrl>/realms/<realm>`. */
    val publicUrl: String,
    val realm: String = "fate",
    /** Required in `aud`: tokens meant for another API are rejected. */
    val audience: String = "fate-api",
    /**
     * Service account used for the Admin API (delete a user, end their sessions); application.yml
     * hands it to the OAuth2 client registration `fate-backend` (see `KeycloakAdminConfig`).
     */
    val clientId: String = "fate-backend",
    val clientSecret: String,
    /** How recent a sign-in must be for operations that re-authenticate (`auth_time`). */
    val reauthenticationMaxAge: Duration = Duration.ofMinutes(5),
    /** How long a deleted account is remembered: longer than any access token lives. */
    val deletedAccountRetention: Duration = Duration.ofDays(1),
) {
    val issuer: String get() = "$publicUrl/realms/$realm"

    val jwkSetUri: String get() = "$url/realms/$realm/protocol/openid-connect/certs"
}
