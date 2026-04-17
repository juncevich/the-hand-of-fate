package com.juncevich.fate.service

import com.juncevich.fate.config.JwtProperties
import com.juncevich.fate.domain.auth.RefreshToken
import com.juncevich.fate.domain.auth.RefreshTokenRepository
import com.juncevich.fate.domain.user.User
import com.juncevich.fate.domain.user.UserRepository
import com.juncevich.fate.security.JwtTokenProvider
import com.juncevich.fate.web.auth.RegisterRequest
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID

class AuthServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val refreshTokenRepository = mockk<RefreshTokenRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>()
    private val jwtProperties = JwtProperties(
        accessSecret = "test-secret-that-is-definitely-long-enough-for-hmac-sha256",
        accessTtlMinutes = 15,
        refreshTtlDays = 30,
    )

    private val authService = AuthService(
        userRepository, refreshTokenRepository, passwordEncoder, jwtTokenProvider, jwtProperties
    )

    private fun makeUser(email: String = "user@test.com") = User(
        email = email,
        passwordHash = "hashedPassword",
        displayName = "Test User",
    )

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    fun `register - creates user and returns tokens`() {
        val request = RegisterRequest(email = "new@test.com", password = "password123", displayName = "New User")
        val user = makeUser(request.email)

        every { userRepository.existsByEmail(request.email) } returns false
        every { passwordEncoder.encode(request.password) } returns "hashedPassword"
        every { userRepository.save(any()) } returns user
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "access-token"
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val result = authService.register(request)

        assertEquals("access-token", result.accessToken)
        assertNotNull(result.refreshToken)
        assertEquals(request.email, result.email)
        verify { userRepository.save(any()) }
        verify { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `register - throws when email already exists`() {
        every { userRepository.existsByEmail("taken@test.com") } returns true

        assertThrows<IllegalStateException> {
            authService.register(RegisterRequest("taken@test.com", "pass12345", "Name"))
        }

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `register - normalizes email to lowercase`() {
        val request = RegisterRequest(email = "UPPER@TEST.COM", password = "password123", displayName = "User")
        val user = makeUser("upper@test.com")

        every { userRepository.existsByEmail("UPPER@TEST.COM") } returns false
        every { passwordEncoder.encode(any()) } returns "hash"
        every { userRepository.save(any()) } returns user
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "token"
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        authService.register(request)

        verify { userRepository.save(match { it.email == "upper@test.com" }) }
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    fun `login - returns tokens for correct credentials`() {
        val user = makeUser()
        every { userRepository.findByEmail("user@test.com") } returns user
        every { passwordEncoder.matches("correctPass", "hashedPassword") } returns true
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "access-token"
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val result = authService.login("user@test.com", "correctPass")

        assertEquals("access-token", result.accessToken)
        assertNotNull(result.refreshToken)
        assertEquals("user@test.com", result.email)
    }

    @Test
    fun `login - throws for unknown email`() {
        every { userRepository.findByEmail("ghost@test.com") } returns null

        assertThrows<IllegalStateException> { authService.login("ghost@test.com", "pass") }
    }

    @Test
    fun `login - throws for wrong password`() {
        val user = makeUser()
        every { userRepository.findByEmail("user@test.com") } returns user
        every { passwordEncoder.matches("wrongPass", "hashedPassword") } returns false

        assertThrows<IllegalStateException> { authService.login("user@test.com", "wrongPass") }
    }

    @Test
    fun `login - error message does not reveal whether email exists`() {
        every { userRepository.findByEmail(any()) } returns null

        val ex1 = assertThrows<IllegalStateException> { authService.login("ghost@test.com", "pass") }

        val user = makeUser()
        every { userRepository.findByEmail("user@test.com") } returns user
        every { passwordEncoder.matches("wrongPass", "hashedPassword") } returns false

        val ex2 = assertThrows<IllegalStateException> { authService.login("user@test.com", "wrongPass") }

        assertEquals(ex1.message, ex2.message)
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    fun `refresh - issues new tokens for valid non-expired token`() {
        val user = makeUser()
        val storedToken = RefreshToken(
            user = user,
            tokenHash = "any-hash",
            expiresAt = Instant.now().plusSeconds(3600),
        )

        every { refreshTokenRepository.findByTokenHash(any()) } returns storedToken
        every { refreshTokenRepository.delete(storedToken) } just Runs
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "new-access-token"
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val result = authService.refresh("raw-token")

        assertEquals("new-access-token", result.accessToken)
        verify { refreshTokenRepository.delete(storedToken) }
    }

    @Test
    fun `refresh - throws and deletes expired token`() {
        val user = makeUser()
        val expiredToken = RefreshToken(
            user = user,
            tokenHash = "expired-hash",
            expiresAt = Instant.now().minusSeconds(1),
        )

        every { refreshTokenRepository.findByTokenHash(any()) } returns expiredToken
        every { refreshTokenRepository.delete(expiredToken) } just Runs

        assertThrows<IllegalStateException> { authService.refresh("expired-raw") }
        verify { refreshTokenRepository.delete(expiredToken) }
    }

    @Test
    fun `refresh - throws when token not found`() {
        every { refreshTokenRepository.findByTokenHash(any()) } returns null

        assertThrows<IllegalStateException> { authService.refresh("unknown-token") }
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    fun `logout - deletes the token if found`() {
        val user = makeUser()
        val token = RefreshToken(user = user, tokenHash = "hash", expiresAt = Instant.now().plusSeconds(3600))
        every { refreshTokenRepository.findByTokenHash(any()) } returns token
        every { refreshTokenRepository.delete(token) } just Runs

        authService.logout("raw-token")

        verify { refreshTokenRepository.delete(token) }
    }

    @Test
    fun `logout - does nothing when token not found`() {
        every { refreshTokenRepository.findByTokenHash(any()) } returns null

        authService.logout("unknown-token")

        verify(exactly = 0) { refreshTokenRepository.delete(any()) }
    }

    // ── logoutAll ─────────────────────────────────────────────────────────────

    @Test
    fun `logoutAll - deletes all tokens for user`() {
        val userId = UUID.randomUUID()
        every { refreshTokenRepository.deleteAllByUserId(userId) } just Runs

        authService.logoutAll(userId)

        verify { refreshTokenRepository.deleteAllByUserId(userId) }
    }
}
