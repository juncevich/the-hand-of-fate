package com.juncevich.fate

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.Date
import java.util.UUID

/**
 * Just enough of Keycloak for the backend's integration tests: realm `fate`'s signing keys, the
 * `fate-backend` service account's token and the two Admin API calls the backend makes. Mints
 * access tokens shaped like Keycloak's (checked against a real Keycloak in KeycloakSmokeIntegrationTest).
 */
object FakeKeycloak {
    /**
     * The issuer browsers would see; the backend reaches this server at [url]. The dev default
     * (dev-start.sh), since it also lands in the committed api/openapi.yaml as Keycloak's address.
     */
    const val PUBLIC_URL = "http://localhost:3000/auth"
    const val ISSUER = "$PUBLIC_URL/realms/fate"
    private const val SERVICE_TOKEN = "service-account-token"

    private val signingKey: RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()

    /** Accounts Keycloak holds; the Admin API answers 404 for anyone else, as Keycloak does. */
    val users: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

    /** Admin API calls the backend made, e.g. `DELETE <id>` or `LOGOUT <id>`. */
    val adminCalls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Accounts whose deletion fails with 500, to exercise retries. */
    val failingDeletes: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

    private val server: HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/auth/realms/fate/protocol/openid-connect/certs") {
                it.respond(200, JWKSet(signingKey.toPublicJWK()).toString())
            }
            createContext("/auth/realms/fate/protocol/openid-connect/token") {
                it.respond(200, """{"access_token":"$SERVICE_TOKEN","expires_in":300,"token_type":"Bearer"}""")
            }
            createContext("/auth/admin/realms/fate/users/") { admin(it) }
            start()
        }

    val url: String = "http://127.0.0.1:${server.address.port}/auth"

    /** An access token as Keycloak issues it to the SPA; override a claim to make it invalid. */
    @Suppress("LongParameterList") // one parameter per claim a test may want to vary
    fun token(
        userId: UUID,
        email: String,
        name: String? = "Kate Keycloak",
        authTime: Instant? = Instant.now(),
        audience: List<String> = listOf("fate-api", "account"),
        emailVerified: Boolean = true,
        issuer: String = ISSUER,
        expiresIn: Duration = Duration.ofMinutes(5),
        key: RSAKey = signingKey,
    ): String {
        val now = Instant.now()
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(expiresIn)))
                .claim("azp", "fate-spa")
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .apply { name?.let { claim("name", it) } }
                .apply { authTime?.let { claim("auth_time", it.epochSecond) } }
                .build()
        val header =
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(key.keyID)
                .type(JOSEObjectType.JWT)
                .build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(key)) }.serialize()
    }

    /** A key Keycloak doesn't publish, with the same key id as the real one. */
    fun foreignKey(): RSAKey = RSAKeyGenerator(2048).keyID(signingKey.keyID).generate()

    fun reset() {
        users.clear()
        adminCalls.clear()
        failingDeletes.clear()
    }

    private fun admin(exchange: HttpExchange) {
        if (exchange.requestHeaders.getFirst("Authorization") != "Bearer $SERVICE_TOKEN") {
            return exchange.respond(401, "")
        }
        // /auth/admin/realms/fate/users/{id}[/logout]
        val parts =
            exchange.requestURI.path
                .removePrefix("/auth/admin/realms/fate/users/")
                .split("/")
        val id = UUID.fromString(parts[0])
        val call =
            when {
                exchange.requestMethod == "DELETE" && parts.size == 1 -> "DELETE"
                exchange.requestMethod == "POST" && parts.getOrNull(1) == "logout" -> "LOGOUT"
                else -> return exchange.respond(405, "")
            }
        adminCalls += "$call $id"
        when {
            call == "DELETE" && id in failingDeletes -> {
                exchange.respond(500, "")
            }

            id !in users -> {
                exchange.respond(404, """{"error":"User not found"}""")
            }

            else -> {
                if (call == "DELETE") users -= id
                exchange.respond(204, "")
            }
        }
    }

    private fun HttpExchange.respond(
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) }
        close()
    }
}
