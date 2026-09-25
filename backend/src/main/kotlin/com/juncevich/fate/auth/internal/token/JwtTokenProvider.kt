package com.juncevich.fate.auth.internal.token

import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Issues access tokens; validation is Spring Security's resource server (see [JwtConfig]). */
@Component
class JwtTokenProvider(
    private val encoder: JwtEncoder,
    private val props: JwtProperties,
) {
    fun createAccessToken(
        userId: UUID,
        email: String,
    ): String {
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .claim(EMAIL_CLAIM, email)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(props.accessTtlMinutes)))
                .build()
        val header = JwsHeader.with(ACCESS_TOKEN_ALGORITHM).type("JWT").build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }
}
