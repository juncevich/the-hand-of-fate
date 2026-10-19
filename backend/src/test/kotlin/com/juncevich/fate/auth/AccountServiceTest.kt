package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.config.AccountProperties
import com.juncevich.fate.auth.internal.config.RateLimitProperties
import com.juncevich.fate.auth.internal.domain.EmailToken
import com.juncevich.fate.auth.internal.domain.EmailTokenPurpose
import com.juncevich.fate.auth.internal.port.EmailTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.RefreshTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.auth.internal.service.AccountService
import com.juncevich.fate.auth.internal.service.AuthService
import com.juncevich.fate.auth.internal.service.AuthTokens
import com.juncevich.fate.auth.internal.service.LoginThrottle
import com.juncevich.fate.auth.internal.service.PasswordConfirmation
import com.juncevich.fate.notification.EmailVerificationPayload
import com.juncevich.fate.notification.NotificationOutbox
import com.juncevich.fate.notification.PasswordResetPayload
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.ErrorCode
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

class AccountServiceTest {
    private val users = mockk<UserRepositoryPort>()
    private val emailTokens = mockk<EmailTokenRepositoryPort>()
    private val refreshTokens = mockk<RefreshTokenRepositoryPort>()
    private val outbox = mockk<NotificationOutbox>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val authService = mockk<AuthService>()
    private val loginThrottle = LoginThrottle(RateLimitProperties(loginFailuresPerAccount = 2))

    private val service =
        AccountService(
            users,
            emailTokens,
            refreshTokens,
            outbox,
            passwordEncoder,
            authService,
            loginThrottle,
            AccountProperties(),
            PasswordConfirmation(passwordEncoder, loginThrottle)
        )

    private val session = mockk<AuthTokens>()

    @BeforeEach
    fun setUp() {
        every { users.save(any()) } answers { firstArg() }
        every { emailTokens.findByUser(any(), any()) } returns null
        every { emailTokens.replace(any()) } just Runs
        every { emailTokens.delete(any()) } just Runs
        every { outbox.record(any()) } just Runs
        every { passwordEncoder.encode(any()) } answers { "hash:${firstArg<String>()}" }
        every { refreshTokens.deleteAllByUserId(any()) } just Runs
        every { authService.startSession(any()) } returns session
    }

    private fun user(verified: Boolean = true) =
        User(
            email = "ann@test.com",
            passwordHash = "hash:old-password",
            displayName = "Ann",
            emailVerifiedAt = if (verified) Instant.now() else null
        )

    private fun sha256(raw: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()))

    private fun tokenFor(
        user: User,
        purpose: EmailTokenPurpose,
        raw: String = "raw-token",
        expiresAt: Instant = Instant.now().plusSeconds(600),
    ): EmailToken {
        val token = EmailToken(user = user, purpose = purpose, tokenHash = sha256(raw), expiresAt = expiresAt)
        every { emailTokens.findByTokenHash(sha256(raw), purpose) } returns token
        return token
    }

    @Test
    fun `register stores an unverified account and mails a link whose token is stored only as a hash`() {
        every { users.existsByEmail("ann@test.com") } returns false
        val stored = slot<EmailToken>()
        every { emailTokens.replace(capture(stored)) } just Runs
        val mailed = slot<EmailVerificationPayload>()
        every { outbox.record(capture(mailed)) } just Runs

        val result = service.register(" Ann@Test.com ", "password123", "Ann")

        assertEquals("ann@test.com", result.email)
        verify { users.save(match { it.emailVerifiedAt == null && it.passwordHash == "hash:password123" }) }
        assertEquals("ann@test.com", mailed.captured.recipientEmail)
        assertEquals(sha256(mailed.captured.token), stored.captured.tokenHash)
        assertEquals(EmailTokenPurpose.VERIFY_EMAIL, stored.captured.purpose)
        verify(exactly = 0) { authService.startSession(any()) }
    }

    @Test
    fun `register rejects a taken address`() {
        every { users.existsByEmail("ann@test.com") } returns true

        assertThrows<ConflictException> { service.register("ann@test.com", "password123", "Ann") }
        verify(exactly = 0) { users.save(any()) }
    }

    @Test
    fun `resend mails nothing for unknown, verified, or just-mailed accounts`() {
        every { users.findByEmail("ghost@test.com") } returns null
        service.resendVerification("ghost@test.com")

        every { users.findByEmail("ann@test.com") } returns user(verified = true)
        service.resendVerification("ann@test.com")

        val unverified = user(verified = false)
        every { users.findByEmail("ann@test.com") } returns unverified
        every { emailTokens.findByUser(unverified.id, EmailTokenPurpose.VERIFY_EMAIL) } returns
            EmailToken(user = unverified, purpose = EmailTokenPurpose.VERIFY_EMAIL, tokenHash = "h", expiresAt = Instant.now())
        service.resendVerification("ann@test.com")

        verify(exactly = 0) { outbox.record(any()) }
    }

    @Test
    fun `resend mails a new link once the cooldown has passed`() {
        val unverified = user(verified = false)
        every { users.findByEmail("ann@test.com") } returns unverified
        every { emailTokens.findByUser(unverified.id, EmailTokenPurpose.VERIFY_EMAIL) } returns
            EmailToken(
                user = unverified,
                purpose = EmailTokenPurpose.VERIFY_EMAIL,
                tokenHash = "h",
                expiresAt = Instant.now(),
                createdAt = Instant.now().minusSeconds(61)
            )

        service.resendVerification("ANN@test.com")

        verify(exactly = 1) { outbox.record(any<EmailVerificationPayload>()) }
    }

    @Test
    fun `verifying confirms the address, spends the token and signs in`() {
        val unverified = user(verified = false)
        val token = tokenFor(unverified, EmailTokenPurpose.VERIFY_EMAIL)

        assertSame(session, service.verifyEmail("raw-token"))

        verify { emailTokens.delete(token) }
        verify { users.save(match { it.emailVerifiedAt != null }) }
        verify { authService.startSession(match { it.id == unverified.id }) }
    }

    @Test
    fun `an unknown or expired token is refused with its code, the expired one spent`() {
        every { emailTokens.findByTokenHash(any(), any()) } returns null
        assertEquals(ErrorCode.INVALID_TOKEN, assertThrows<BadRequestException> { service.verifyEmail("nope") }.code)

        val token = tokenFor(user(false), EmailTokenPurpose.VERIFY_EMAIL, expiresAt = Instant.now().minusSeconds(1))
        assertEquals(ErrorCode.TOKEN_EXPIRED, assertThrows<BadRequestException> { service.verifyEmail("raw-token") }.code)
        verify { emailTokens.delete(token) }
        verify(exactly = 0) { authService.startSession(any()) }
    }

    @Test
    fun `a password reset link is mailed only to known addresses`() {
        every { users.findByEmail("ghost@test.com") } returns null
        service.requestPasswordReset("ghost@test.com")
        verify(exactly = 0) { outbox.record(any()) }

        every { users.findByEmail("ann@test.com") } returns user()
        service.requestPasswordReset("ann@test.com")
        verify(exactly = 1) { outbox.record(any<PasswordResetPayload>()) }
    }

    @Test
    fun `resetting sets the password, confirms the address and ends every session`() {
        val unverified = user(verified = false)
        tokenFor(unverified, EmailTokenPurpose.RESET_PASSWORD)

        service.resetPassword("raw-token", "new-password")

        verify { users.save(match { it.passwordHash == "hash:new-password" && it.emailVerifiedAt != null }) }
        verify { refreshTokens.deleteAllByUserId(unverified.id) }
    }

    @Test
    fun `a verification token cannot reset a password`() {
        every { emailTokens.findByTokenHash(sha256("raw-token"), EmailTokenPurpose.RESET_PASSWORD) } returns null

        assertThrows<BadRequestException> { service.resetPassword("raw-token", "new-password") }
        verify(exactly = 0) { users.save(any()) }
    }

    @Test
    fun `changing the password checks the current one and starts a fresh session`() {
        val account = user()
        every { users.findById(account.id) } returns account
        every { passwordEncoder.matches("old-password", "hash:old-password") } returns true

        assertSame(session, service.changePassword(account.id, "old-password", "new-password"))

        verify { users.save(match { it.passwordHash == "hash:new-password" }) }
        verify { refreshTokens.deleteAllByUserId(account.id) }
    }

    @Test
    fun `a wrong current password is a 400 and counts towards the login throttle`() {
        val account = user()
        every { users.findById(account.id) } returns account
        every { passwordEncoder.matches(any(), any()) } returns false

        repeat(2) {
            val ex = assertThrows<BadRequestException> { service.changePassword(account.id, "guess", "new-password") }
            assertEquals(ErrorCode.WRONG_PASSWORD, ex.code)
        }
        assertNotNull(
            assertThrows<com.juncevich.fate.auth.internal.service.LoginThrottledException> {
                service.changePassword(account.id, "guess", "new-password")
            }
        )
        verify(exactly = 0) { users.save(any()) }
    }

    @Test
    fun `changing the password of a missing user fails`() {
        every { users.findById(any()) } returns null

        assertThrows<com.juncevich.fate.shared.NotFoundException> {
            service.changePassword(UUID.randomUUID(), "a", "new-password")
        }
    }
}
