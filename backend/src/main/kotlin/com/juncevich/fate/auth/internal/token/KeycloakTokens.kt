package com.juncevich.fate.auth.internal.token

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.internal.keycloak.KeycloakProperties
import com.juncevich.fate.auth.internal.service.KeycloakIdentity
import com.juncevich.fate.auth.internal.service.KeycloakUserProvisioner
import com.juncevich.fate.shared.canonicalEmail
import com.nimbusds.jwt.JWTParser
import org.springframework.core.convert.converter.Converter
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.web.client.RestTemplate
import java.text.ParseException
import java.time.Duration
import java.util.UUID

internal const val AUTH_TIME_CLAIM = "auth_time"
private const val EMAIL_VERIFIED_CLAIM = "email_verified"
private const val NAME_CLAIM = "name"
private val JWKS_TIMEOUT: Duration = Duration.ofSeconds(5)

/**
 * RS256 access tokens of realm `fate`, verified with the keys Keycloak publishes (fetched from the
 * backend's address of Keycloak, cached and refetched for an unknown key id).
 */
internal fun keycloakAccessTokenDecoder(props: KeycloakProperties): JwtDecoder {
    val requestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(JWKS_TIMEOUT)
            setReadTimeout(JWKS_TIMEOUT)
        }
    return NimbusJwtDecoder
        .withJwkSetUri(props.jwkSetUri)
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .restOperations(RestTemplate(requestFactory))
        .build()
        .apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefaultWithIssuer(props.issuer),
                    KeycloakClaimsValidator(props.audience)
                )
            )
        }
}

/**
 * Accepts only tokens meant for this API (`aud`) whose user is identified by a UUID subject and a
 * verified email — what [KeycloakAccessTokenConverter] relies on. A failure is a 401, not a 500.
 */
internal class KeycloakClaimsValidator(
    private val audience: String,
) : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val problem =
            when {
                audience !in token.audience.orEmpty() -> {
                    "Access token is not meant for $audience"
                }

                token.subject?.let { runCatching { UUID.fromString(it) }.isSuccess } != true -> {
                    "Access token must carry a UUID subject"
                }

                token.getClaimAsString(EMAIL_CLAIM).isNullOrBlank() -> {
                    "Access token must carry an email claim"
                }

                token.getClaimAsBoolean(EMAIL_VERIFIED_CLAIM) != true -> {
                    "The email address is not verified"
                }

                else -> {
                    return OAuth2TokenValidatorResult.success()
                }
            }
        return OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", problem, null))
    }
}

/**
 * Sends each token to the decoder of whoever issued it: Keycloak's carry its issuer, the app's own
 * HS256 tokens carry none (they go away with the app's own login, docs/plans/keycloak-auth.md).
 */
internal class IssuerRoutingJwtDecoder(
    private val keycloakIssuer: String,
    private val keycloak: JwtDecoder,
    private val own: JwtDecoder,
) : JwtDecoder {
    override fun decode(token: String): Jwt {
        val issuer =
            try {
                JWTParser.parse(token).jwtClaimsSet.issuer
            } catch (ex: ParseException) {
                throw BadJwtException("Malformed access token", ex)
            }
        return if (issuer == keycloakIssuer) keycloak.decode(token) else own.decode(token)
    }
}

/** Converts each token with the converter for whoever issued it, see [IssuerRoutingJwtDecoder]. */
internal class IssuerRoutingAuthenticationConverter(
    private val keycloakIssuer: String,
    private val keycloak: Converter<Jwt, AbstractAuthenticationToken>,
    private val own: Converter<Jwt, AbstractAuthenticationToken>,
) : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val converter = if (jwt.getClaimAsString(JwtClaimNames.ISS) == keycloakIssuer) keycloak else own
        return checkNotNull(converter.convert(jwt)) { "Token converters never return null" }
    }
}

/**
 * Exposes a Keycloak token's user as [AuthenticatedUser], creating their `users` row on first use.
 * A token of a deleted account is rejected (401) for the rest of its lifetime.
 */
class KeycloakAccessTokenConverter(
    private val provisioner: KeycloakUserProvisioner,
) : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        // Subject and email are guaranteed by KeycloakClaimsValidator before conversion
        val identity =
            KeycloakIdentity(
                id = UUID.fromString(jwt.subject),
                email = canonicalEmail(requireNotNull(jwt.getClaimAsString(EMAIL_CLAIM))),
                name = jwt.getClaimAsString(NAME_CLAIM)
            )
        if (!provisioner.ensureProvisioned(identity)) {
            throw InvalidBearerTokenException("The account was deleted")
        }
        return UsernamePasswordAuthenticationToken(
            AuthenticatedUser(identity.id, identity.email, jwt.getClaimAsInstant(AUTH_TIME_CLAIM)),
            jwt,
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
    }
}
