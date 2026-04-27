package com.juncevich.fate.security

import com.juncevich.fate.config.JwtProperties
import io.jsonwebtoken.JwtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class JwtTokenProviderTest {

    private val props = JwtProperties(
        accessSecret = "test-secret-that-is-at-least-256-bits-long-for-hmac-sha256",
        accessTtlMinutes = 15,
        refreshTtlDays = 30,
    )

    private val provider = JwtTokenProvider(props)

    @Test
    fun `createAccessToken - returns non-empty token`() {
        val token = provider.createAccessToken(UUID.randomUUID(), "user@test.com")
        assertNotNull(token)
        assert(token.isNotBlank())
    }

    @Test
    fun `getUserId - returns the uuid embedded in token`() {
        val userId = UUID.randomUUID()
        val token = provider.createAccessToken(userId, "user@test.com")

        assertEquals(userId, provider.getUserId(token))
    }

    @Test
    fun `getEmail - returns the email embedded in token`() {
        val email = "test@example.com"
        val token = provider.createAccessToken(UUID.randomUUID(), email)

        assertEquals(email, provider.getEmail(token))
    }

    @Test
    fun `validateAndGetClaims - throws on tampered token`() {
        val token = provider.createAccessToken(UUID.randomUUID(), "user@test.com")
        val tampered = token.dropLast(5) + "XXXXX"

        assertThrows<JwtException> { provider.validateAndGetClaims(tampered) }
    }

    @Test
    fun `validateAndGetClaims - throws on token signed with different key`() {
        val otherProps = JwtProperties(
            accessSecret = "completely-different-secret-key-that-is-256-bits-long-xxxxxx",
            accessTtlMinutes = 15,
            refreshTtlDays = 30,
        )
        val foreignToken = JwtTokenProvider(otherProps).createAccessToken(UUID.randomUUID(), "x@x.com")

        assertThrows<JwtException> { provider.validateAndGetClaims(foreignToken) }
    }

    @Test
    fun `createAccessToken - expired token throws on validation`() {
        val expiredProps = JwtProperties(
            accessSecret = "test-secret-that-is-at-least-256-bits-long-for-hmac-sha256",
            accessTtlMinutes = -1,
            refreshTtlDays = 30,
        )
        val expiredProvider = JwtTokenProvider(expiredProps)
        val token = expiredProvider.createAccessToken(UUID.randomUUID(), "user@test.com")

        assertThrows<JwtException> { provider.validateAndGetClaims(token) }
    }
}
