package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.config.RateLimitProperties
import com.juncevich.fate.auth.internal.domain.RefreshToken
import com.juncevich.fate.auth.internal.port.RefreshTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.auth.internal.service.AuthService
import com.juncevich.fate.auth.internal.service.LoginThrottle
import com.juncevich.fate.auth.internal.service.LoginThrottledException
import com.juncevich.fate.auth.internal.token.JwtProperties
import com.juncevich.fate.auth.internal.token.JwtTokenProvider
import com.juncevich.fate.shared.ConflictException
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID

class AuthServiceTest {
    private val userRepositoryPort = mockk<UserRepositoryPort>()
    private val refreshTokenRepositoryPort = mockk<RefreshTokenRepositoryPort>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>()
    private val jwtProperties =
        JwtProperties(
            accessSecret = "test-secret-that-is-definitely-long-enough-for-hmac-sha256",
            accessTtlMinutes = 15,
            refreshTtlDays = 30
        )

    private val authService =
        AuthService(
            userRepositoryPort,
            refreshTokenRepositoryPort,
            passwordEncoder,
            jwtTokenProvider,
            jwtProperties,
            LoginThrottle(RateLimitProperties(loginFailuresPerAccount = 2))
        )

    private fun makeUser(email: String = "user@test.com") =
        User(
            email = email,
            passwordHash = "hashedPassword",
            displayName = "Test User"
        )

    @Test
    fun `register - creates user and returns tokens`() {
        val user = makeUser("new@test.com")

        every { userRepositoryPort.existsByEmail("new@test.com") } returns false
        every { passwordEncoder.encode("password123") } returns "hashedPassword"
        every { userRepositoryPort.save(any()) } returns user
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "access-token"
        every { refreshTokenRepositoryPort.save(any()) } answers { firstArg() }

        val result = authService.register("new@test.com", "password123", "New User")

        assertEquals("access-token", result.accessToken)
        assertNotNull(result.refreshToken)
        assertEquals("new@test.com", result.email)
        assertEquals(user.id, result.userId)
        verify { userRepositoryPort.save(any()) }
        verify { refreshTokenRepositoryPort.save(any()) }
    }

    @Test
    fun `register - throws when email already exists`() {
        every { userRepositoryPort.existsByEmail("taken@test.com") } returns true

        assertThrows<ConflictException> {
            authService.register("taken@test.com", "pass12345", "Name")
        }

        verify(exactly = 0) { userRepositoryPort.save(any()) }
    }

    @Test
    fun `register - normalizes email to lowercase`() {
        val user = makeUser("upper@test.com")

        every { userRepositoryPort.existsByEmail("upper@test.com") } returns false
        every { passwordEncoder.encode(any()) } returns "hash"
        every { userRepositoryPort.save(any()) } returns user
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "token"
        every { refreshTokenRepositoryPort.save(any()) } answers { firstArg() }

        authService.register("UPPER@TEST.COM", "password123", "User")

        verify { userRepositoryPort.save(match { it.email == "upper@test.com" }) }
    }

    @Test
    fun `login - throttles an account after repeated failures without checking the password`() {
        every { userRepositoryPort.findByEmail("user@test.com") } returns makeUser()
        every { passwordEncoder.matches(any(), any()) } returns false

        repeat(2) { assertThrows<BadCredentialsException> { authService.login(" User@Test.com", "wrong") } }
        val ex = assertThrows<LoginThrottledException> { authService.login("user@test.com", "wrong") }

        assertTrue(ex.retryAfterSeconds in 1..900)
        verify(exactly = 2) { passwordEncoder.matches(any(), any()) }
    }

    @Test
    fun `login - throttles unknown emails the same way, so throttling does not reveal accounts`() {
        every { userRepositoryPort.findByEmail("ghost@test.com") } returns null
        every { passwordEncoder.encode(any()) } returns "dummy-hash"
        every { passwordEncoder.matches(any(), any()) } returns false

        repeat(2) { assertThrows<BadCredentialsException> { authService.login("ghost@test.com", "x") } }
        assertThrows<LoginThrottledException> { authService.login("ghost@test.com", "x") }
    }

    @Test
    fun `login - a successful login clears earlier failures`() {
        every { userRepositoryPort.findByEmail("user@test.com") } returns makeUser()
        every { passwordEncoder.matches("wrong", any()) } returns false
        every { passwordEncoder.matches("correctPass", any()) } returns true
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "access-token"
        every { refreshTokenRepositoryPort.save(any()) } answers { firstArg() }

        assertThrows<BadCredentialsException> { authService.login("user@test.com", "wrong") }
        authService.login("user@test.com", "correctPass")
        assertThrows<BadCredentialsException> { authService.login("user@test.com", "wrong") }

        // Two failures in total, but the success in between reset the budget.
        assertThrows<BadCredentialsException> { authService.login("user@test.com", "wrong") }
    }

    @Test
    fun `login - returns tokens for correct credentials`() {
        val user = makeUser()
        every { userRepositoryPort.findByEmail("user@test.com") } returns user
        every { passwordEncoder.matches("correctPass", "hashedPassword") } returns true
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "access-token"
        every { refreshTokenRepositoryPort.save(any()) } answers { firstArg() }

        val result = authService.login("user@test.com", "correctPass")

        assertEquals("access-token", result.accessToken)
        assertNotNull(result.refreshToken)
        assertEquals("user@test.com", result.email)
    }

    @Test
    fun `login - returns a versioned avatar url only when the user has an avatar`() {
        val withAvatar = makeUser().apply { avatarUpdatedAt = Instant.ofEpochMilli(1234) }
        every { userRepositoryPort.findByEmail("user@test.com") } returns withAvatar
        every { passwordEncoder.matches(any(), any()) } returns true
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "access-token"
        every { refreshTokenRepositoryPort.save(any()) } answers { firstArg() }

        assertEquals(
            "/api/v1/users/${withAvatar.id}/avatar?v=1234",
            authService.login("user@test.com", "pass").avatarUrl
        )

        every { userRepositoryPort.findByEmail("user@test.com") } returns makeUser()
        assertNull(authService.login("user@test.com", "pass").avatarUrl)
    }

    @Test
    fun `login - throws for unknown email`() {
        every { userRepositoryPort.findByEmail("ghost@test.com") } returns null
        every { passwordEncoder.encode(any()) } returns "dummy-hash"
        every { passwordEncoder.matches(any(), any()) } returns false

        assertThrows<BadCredentialsException> { authService.login("ghost@test.com", "pass") }
    }

    @Test
    fun `login - still hashes the password when email is unknown (timing side-channel mitigation)`() {
        every { userRepositoryPort.findByEmail("ghost@test.com") } returns null
        every { passwordEncoder.encode(any()) } returns "dummy-hash"
        every { passwordEncoder.matches(any(), "dummy-hash") } returns false

        assertThrows<BadCredentialsException> { authService.login("ghost@test.com", "pass") }

        verify { passwordEncoder.matches("pass", "dummy-hash") }
    }

    @Test
    fun `login - throws for wrong password`() {
        val user = makeUser()
        every { userRepositoryPort.findByEmail("user@test.com") } returns user
        every { passwordEncoder.matches("wrongPass", "hashedPassword") } returns false

        assertThrows<BadCredentialsException> { authService.login("user@test.com", "wrongPass") }
    }

    @Test
    fun `login - error message does not reveal whether email exists`() {
        every { userRepositoryPort.findByEmail(any()) } returns null
        every { passwordEncoder.encode(any()) } returns "dummy-hash"
        every { passwordEncoder.matches(any(), any()) } returns false

        val ex1 = assertThrows<BadCredentialsException> { authService.login("ghost@test.com", "pass") }

        val user = makeUser()
        every { userRepositoryPort.findByEmail("user@test.com") } returns user
        every { passwordEncoder.matches("wrongPass", "hashedPassword") } returns false

        val ex2 = assertThrows<BadCredentialsException> { authService.login("user@test.com", "wrongPass") }

        assertEquals(ex1.message, ex2.message)
    }

    @Test
    fun `refresh - issues new tokens for valid non-expired token`() {
        val user = makeUser()
        val storedToken =
            RefreshToken(
                user = user,
                tokenHash = "any-hash",
                expiresAt = Instant.now().plusSeconds(3600)
            )

        every { refreshTokenRepositoryPort.findByTokenHash(any()) } returns storedToken
        every { refreshTokenRepositoryPort.delete(storedToken) } just Runs
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "new-access-token"
        every { refreshTokenRepositoryPort.save(any()) } answers { firstArg() }

        val result = authService.refresh("raw-token")

        assertEquals("new-access-token", result.accessToken)
        verify { refreshTokenRepositoryPort.delete(storedToken) }
    }

    @Test
    fun `refresh - throws and deletes expired token`() {
        val user = makeUser()
        val expiredToken =
            RefreshToken(
                user = user,
                tokenHash = "expired-hash",
                expiresAt = Instant.now().minusSeconds(1)
            )

        every { refreshTokenRepositoryPort.findByTokenHash(any()) } returns expiredToken
        every { refreshTokenRepositoryPort.delete(expiredToken) } just Runs

        assertThrows<BadCredentialsException> { authService.refresh("expired-raw") }
        verify { refreshTokenRepositoryPort.delete(expiredToken) }
    }

    @Test
    fun `refresh - throws when token not found`() {
        every { refreshTokenRepositoryPort.findByTokenHash(any()) } returns null

        assertThrows<BadCredentialsException> { authService.refresh("unknown-token") }
    }

    @Test
    fun `logout - deletes the token if found`() {
        val user = makeUser()
        val token = RefreshToken(user = user, tokenHash = "hash", expiresAt = Instant.now().plusSeconds(3600))
        every { refreshTokenRepositoryPort.findByTokenHash(any()) } returns token
        every { refreshTokenRepositoryPort.delete(token) } just Runs

        authService.logout("raw-token")

        verify { refreshTokenRepositoryPort.delete(token) }
    }

    @Test
    fun `logout - does nothing when token not found`() {
        every { refreshTokenRepositoryPort.findByTokenHash(any()) } returns null

        authService.logout("unknown-token")

        verify(exactly = 0) { refreshTokenRepositoryPort.delete(any()) }
    }

    @Test
    fun `logoutAll - deletes all tokens for user`() {
        val userId = UUID.randomUUID()
        every { refreshTokenRepositoryPort.deleteAllByUserId(userId) } just Runs

        authService.logoutAll(userId)

        verify { refreshTokenRepositoryPort.deleteAllByUserId(userId) }
    }
}
