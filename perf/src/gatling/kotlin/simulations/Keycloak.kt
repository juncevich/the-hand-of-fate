package simulations

import io.gatling.javaapi.core.ChainBuilder
import io.gatling.javaapi.core.CoreDsl.exec
import io.gatling.javaapi.core.CoreDsl.jsonPath
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status

/**
 * Keycloak (docs/adr/0001) — reached directly (`docker-compose.infra.yml` publishes it on :8180) or
 * through the gateway (`-DkeycloakUrl=http://localhost:30080/auth` for minikube).
 */
val keycloakUrl: String = System.getProperty("keycloakUrl", "http://localhost:8180/auth")

/**
 * Signs the demo user in through `fate-test`, the client with direct grants that exists only in the
 * dev/e2e realm (infra/keycloak/realm-dev), and saves the access token as `accessToken`.
 */
fun signIn(): ChainBuilder =
    exec(
        http("Keycloak Sign-in")
            .post("$keycloakUrl/realms/fate/protocol/openid-connect/token")
            .asFormUrlEncoded()
            .formParam("grant_type", "password")
            .formParam("client_id", "fate-test")
            .formParam("username", "admin@admin.com")
            .formParam("password", "admin123")
            .check(status().`is`(200))
            .check(jsonPath("$.access_token").saveAs("accessToken")),
    )
