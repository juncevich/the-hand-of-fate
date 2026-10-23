package com.juncevich.fate.account.keycloak

import com.fasterxml.jackson.annotation.JsonProperty
import com.juncevich.fate.account.domain.IdentityAdmin
import io.micrometer.observation.ObservationRegistry
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val ADMIN_TIMEOUT: Duration = Duration.ofSeconds(10)

// A token is renewed this long before Keycloak would consider it expired.
private val TOKEN_EXPIRY_MARGIN: Duration = Duration.ofSeconds(30)

/**
 * Keycloak's Admin API as the `fate-backend` service account (client credentials; its role
 * `manage-users` is granted in infra/keycloak/realm/fate.yaml).
 */
@Component
class KeycloakAdminClient(
    private val props: KeycloakProperties,
    observationRegistry: ObservationRegistry,
    private val clock: Clock,
) : IdentityAdmin {
    private val http =
        RestClient
            .builder()
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(ADMIN_TIMEOUT).build()).apply {
                    setReadTimeout(ADMIN_TIMEOUT)
                }
            ).observationRegistry(observationRegistry)
            .build()

    @Volatile
    private var token: ServiceToken? = null

    override fun deleteUser(userId: UUID) = ignoringNotFound { admin().delete().uri("/users/{id}", userId) }

    override fun logoutUser(userId: UUID) = ignoringNotFound { admin().post().uri("/users/{id}/logout", userId) }

    private fun admin(): RestClient =
        http
            .mutate()
            .baseUrl(props.adminUri)
            .defaultHeaders { it.setBearerAuth(accessToken()) }
            .build()

    // An account that is not in Keycloak (e.g. one from before Keycloak) has nothing to delete there.
    private fun ignoringNotFound(request: () -> RestClient.RequestHeadersSpec<*>) {
        try {
            request().retrieve().toBodilessEntity()
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode != HttpStatus.NOT_FOUND) throw ex
        }
    }

    private fun accessToken(): String {
        token?.takeIf { clock.instant().isBefore(it.renewAt) }?.let { return it.value }
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "client_credentials")
                add("client_id", props.clientId)
                add("client_secret", props.clientSecret)
            }
        val response =
            checkNotNull(
                http
                    .post()
                    .uri(props.tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body<TokenResponse>()
            ) { "Keycloak returned no token" }
        val renewAt = clock.instant().plusSeconds(response.expiresIn).minus(TOKEN_EXPIRY_MARGIN)
        return ServiceToken(response.accessToken, renewAt).also { token = it }.value
    }

    private data class ServiceToken(
        val value: String,
        val renewAt: Instant,
    )
}

internal data class TokenResponse(
    @param:JsonProperty("access_token") val accessToken: String,
    @param:JsonProperty("expires_in") val expiresIn: Long,
)
