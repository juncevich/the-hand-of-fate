package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.domain.RefreshToken
import com.juncevich.fate.auth.internal.port.RefreshTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.auth.internal.service.AuthService
import com.juncevich.fate.auth.internal.service.RegisterRequest
import com.juncevich.fate.auth.internal.token.JwtProperties
import com.juncevich.fate.auth.internal.token.JwtTokenProvider
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
            jwtProperties
        )

    private fun makeUser(email: String = "user@test.com") =
        User(
            email = email,
            passwordHash = "hashedPassword",
            displayName = "Test User"
        )

    @Test
    fun `register - creates user and returns tokens`() {
        val request = RegisterRequest(email = "new@test.com", password = "password123", displayName = "New User")
        val user = makeUser(request.email)

        every { userRepositoryPort.existsByEmail(request.email) } returns false
        every { passwordEncoder.encode(request.password) } returns "hashedPassword"
        every { userRepositoryPort.save(any()) } returns user
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "access-token"
        every { refreshTokenRepositoryPort.save(any()) } answers { firstArg() }

        val result = authService.register(request)

        assertEquals("access-token", result.response.accessToken)
        assertNotNull(result.refreshToken)
        assertEquals(request.email, result.response.email)
        verify { userRepositoryPort.save(any()) }
        verify { refreshTokenRepositoryPort.save(any()) }
    }

    @Test
    fun `register - throws when email already exists`() {
        every { userRepositoryPort.existsByEmail("taken@test.com") } returns true

        assertThrows<IllegalStateException> {
            authService.register(RegisterRequest("taken@test.com", "pass12345", "Name"))
        }

        verify(exactly = 0) { userRepositoryPort.save(any()) }
    }

    @Test
    fun `register - normalizes email to lowercase`() {
        val request = RegisterRequest(email = "UPPER@TEST.COM", password = "password123", displayName = "User")
        val user = makeUser("upper@test.com")

        every { userRepositoryPort.existsByEmail("UPPER@TEST.COM") } returns false
        every { passwordEncoder.encode(any()) } returns "hash"
        every { userRepositoryPort.save(any()) } returns user
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "token"
        every { refreshTokenRepositoryPort.save(any()) } answers { firstArg() }

        authService.register(request)

        verify { userRepositoryPort.save(match { it.email == "upper@test.com" }) }
    }

    @Test
    fun `login - returns tokens for correct credentials`() {
        val user = makeUser()
        every { userRepositoryPort.findByEmail("user@test.com") } returns user
        every { passwordEncoder.matches("correctPass", "hashedPassword") } returns true
        every { jwtTokenProvider.createAccessToken(any(), any()) } returns "access-token"
        every { refreshTokenRepositoryPort.save(any()) } answers { firstArg() }

        val result = authService.login("user@test.com", "correctPass")

        assertEquals("access-token", result.response.accessToken)
        assertNotNull(result.refreshToken)
        assertEquals("user@test.com", result.response.email)
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

        assertEquals("new-access-token", result.response.accessToken)
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
