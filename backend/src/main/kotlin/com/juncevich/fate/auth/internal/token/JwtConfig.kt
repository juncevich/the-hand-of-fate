package com.juncevich.fate.auth.internal.token

import com.juncevich.fate.auth.AuthenticatedUser
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Access tokens are HS256 JWTs signed with `jwt.access-secret` (see [JwtTokenProvider]). */
internal val ACCESS_TOKEN_ALGORITHM = MacAlgorithm.HS256

internal const val EMAIL_CLAIM = "email"

@Configuration
class JwtConfig {
    @Bean
    fun jwtEncoder(props: JwtProperties): JwtEncoder = accessTokenEncoder(props.accessSecret)

    @Bean
    fun jwtDecoder(props: JwtProperties): JwtDecoder = accessTokenDecoder(props.accessSecret)

    @Bean
    fun accessTokenAuthenticationConverter(): Converter<Jwt, AbstractAuthenticationToken> =
        AccessTokenAuthenticationConverter()
}

private fun secretKey(secret: String): SecretKey = SecretKeySpec(secret.toByteArray(), ACCESS_TOKEN_ALGORITHM.getName())

internal fun accessTokenEncoder(secret: String): JwtEncoder =
    NimbusJwtEncoder(ImmutableSecret<SecurityContext>(secretKey(secret)))

internal fun accessTokenDecoder(secret: String): JwtDecoder =
    NimbusJwtDecoder
        .withSecretKey(secretKey(secret))
        .macAlgorithm(ACCESS_TOKEN_ALGORITHM)
        .build()
        .apply {
            // exp/nbf checks (with the default clock skew) plus our own claim requirements
            setJwtValidator(DelegatingOAuth2TokenValidator(JwtValidators.createDefault(), AccessTokenClaimsValidator))
        }

/** Rejects tokens that could not be turned into an [AuthenticatedUser], so they yield 401, not 500. */
private object AccessTokenClaimsValidator : OAuth2TokenValidator<Jwt> {
    private val invalid =
        OAuth2TokenValidatorResult.failure(
            OAuth2Error("invalid_token", "Access token must carry a UUID subject and an email claim", null)
        )

    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val validSubject = token.subject?.let { runCatching { UUID.fromString(it) }.isSuccess } ?: false
        val hasEmail = !token.getClaimAsString(EMAIL_CLAIM).isNullOrBlank()
        return if (validSubject && hasEmail) OAuth2TokenValidatorResult.success() else invalid
    }
}

/** Exposes the token's identity as [AuthenticatedUser], the principal controllers inject. */
class AccessTokenAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken =
        UsernamePasswordAuthenticationToken(
            // Both claims are guaranteed by AccessTokenClaimsValidator before conversion
            AuthenticatedUser(UUID.fromString(jwt.subject), requireNotNull(jwt.getClaimAsString(EMAIL_CLAIM))),
            jwt,
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
}
