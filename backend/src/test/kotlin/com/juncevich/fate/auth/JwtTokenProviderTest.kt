package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.token.AccessTokenAuthenticationConverter
import com.juncevich.fate.auth.internal.token.JwtProperties
import com.juncevich.fate.auth.internal.token.JwtTokenProvider
import com.juncevich.fate.auth.internal.token.accessTokenDecoder
import com.juncevich.fate.auth.internal.token.accessTokenEncoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Duration
import java.util.UUID

class JwtTokenProviderTest {
    private val secret = "test-secret-that-is-at-least-256-bits-long-for-hmac-sha256"
    private val props = JwtProperties(accessSecret = secret, accessTtlMinutes = 15, refreshTtlDays = 30)

    private val provider = JwtTokenProvider(accessTokenEncoder(secret), props)
    private val decoder = accessTokenDecoder(secret)

    @Test
    fun `issued token round-trips through the decoder with subject and email`() {
        val userId = UUID.randomUUID()
        val jwt = decoder.decode(provider.createAccessToken(userId, "user@example.com"))

        assertEquals(userId.toString(), jwt.subject)
        assertEquals("user@example.com", jwt.getClaimAsString("email"))
    }

    @Test
    fun `token is HS256-signed and expires after the configured TTL`() {
        val jwt = decoder.decode(provider.createAccessToken(UUID.randomUUID(), "u@e.com"))

        assertEquals("HS256", jwt.headers["alg"].toString())
        assertEquals(Duration.ofMinutes(15), Duration.between(jwt.issuedAt, jwt.expiresAt))
    }

    @Test
    fun `tampered token is rejected`() {
        val token = provider.createAccessToken(UUID.randomUUID(), "user@example.com")
        val tampered = token.dropLast(5) + "XXXXX"

        assertThrows<JwtException> { decoder.decode(tampered) }
    }

    @Test
    fun `token signed with a different secret is rejected`() {
        val other = JwtTokenProvider(accessTokenEncoder("another-secret-that-is-at-least-256-bits-long"), props)

        assertThrows<JwtException> { decoder.decode(other.createAccessToken(UUID.randomUUID(), "u@e.com")) }
    }

    @Test
    fun `converter exposes the token identity as AuthenticatedUser`() {
        val userId = UUID.randomUUID()
        val jwt = decoder.decode(provider.createAccessToken(userId, "extract@test.com"))

        val auth = AccessTokenAuthenticationConverter().convert(jwt)

        assertEquals(AuthenticatedUser(userId, "extract@test.com"), auth.principal)
        assertEquals(listOf("ROLE_USER"), auth.authorities.map { it.authority })
    }
}
