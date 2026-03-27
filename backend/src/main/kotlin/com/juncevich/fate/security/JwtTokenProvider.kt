package com.juncevich.fate.security

import com.juncevich.fate.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(private val props: JwtProperties) {

    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(props.accessSecret.toByteArray())
    }

    fun createAccessToken(userId: UUID, email: String): String {
        val now = Date()
        val expiry = Date(now.time + props.accessTtlMinutes * 60 * 1000)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey)
            .compact()
    }

    fun validateAndGetClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload

    fun getUserId(token: String): UUID =
        UUID.fromString(validateAndGetClaims(token).subject)

    fun getEmail(token: String): String =
        validateAndGetClaims(token)["email"] as String
}
