package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.token.JwtProperties
import com.juncevich.fate.auth.internal.token.JwtTokenProvider
import io.jsonwebtoken.JwtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class JwtTokenProviderTest {
    private val props =
        JwtProperties(
            accessSecret = "test-secret-that-is-at-least-256-bits-long-for-hmac-sha256",
            accessTtlMinutes = 15,
            refreshTtlDays = 30
        )

    private val provider = JwtTokenProvider(props)

    @Test
    fun `createAccessToken returns non-blank token`() {
        val token = provider.createAccessToken(UUID.randomUUID(), "user@example.com")
        assertNotNull(token)
        assert(token.isNotBlank())
    }

    @Test
    fun `validateAndGetClaims returns correct subject and email`() {
        val userId = UUID.randomUUID()
        val email = "user@example.com"
        val token = provider.createAccessToken(userId, email)

        val claims = provider.validateAndGetClaims(token)

        assertEquals(userId.toString(), claims.subject)
        assertEquals(email, claims["email"])
    }

    @Test
    fun `validateAndGetClaims throws for tampered token`() {
        val token = provider.createAccessToken(UUID.randomUUID(), "user@example.com")
        val tampered = token.dropLast(5) + "XXXXX"

        assertThrows<JwtException> { provider.validateAndGetClaims(tampered) }
    }

    @Test
    fun `getUserId extracts correct UUID`() {
        val userId = UUID.randomUUID()
        val token = provider.createAccessToken(userId, "u@e.com")
        assertEquals(userId, provider.getUserId(token))
    }

    @Test
    fun `getEmail extracts correct email`() {
        val token = provider.createAccessToken(UUID.randomUUID(), "extract@test.com")
        assertEquals("extract@test.com", provider.getEmail(token))
    }
}
