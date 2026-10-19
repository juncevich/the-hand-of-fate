package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.config.AccountProperties
import com.juncevich.fate.auth.internal.domain.EmailToken
import com.juncevich.fate.auth.internal.domain.EmailTokenPurpose
import com.juncevich.fate.auth.internal.port.EmailTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.RefreshTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.notification.EmailVerificationPayload
import com.juncevich.fate.notification.NotificationOutbox
import com.juncevich.fate.notification.PasswordResetPayload
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.canonicalEmail
import com.juncevich.fate.shared.ensureState
import com.juncevich.fate.shared.normalizeEmail
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

private const val TOKEN_BYTES = 32

data class RegistrationResult(
    val email: String,
)

/**
 * Account lifecycle: registration with email verification, password reset by a mailed link
 * and password change (deletion: [AccountDeletionService]). Mailed links carry a random token; only its SHA-256 is
 * stored, and it is consumed on first use. Requests that take an email address never reveal
 * whether an account exists.
 */
@Service
@Transactional
class AccountService(
    private val users: UserRepositoryPort,
    private val emailTokens: EmailTokenRepositoryPort,
    private val refreshTokens: RefreshTokenRepositoryPort,
    private val outbox: NotificationOutbox,
    private val passwordEncoder: PasswordEncoder,
    private val authService: AuthService,
    private val loginThrottle: LoginThrottle,
    private val properties: AccountProperties,
    private val passwordConfirmation: PasswordConfirmation,
) {
    private val random = SecureRandom()

    fun register(
        email: String,
        password: String,
        displayName: String,
    ): RegistrationResult {
        val normalizedEmail = normalizeEmail(email)
        ensureState(
            !users.existsByEmail(normalizedEmail),
            ErrorCode.EMAIL_ALREADY_REGISTERED
        ) { "Email already registered" }
        val user =
            users.save(
                User(
                    email = normalizedEmail,
                    passwordHash = encode(password),
                    displayName = displayName
                )
            )
        sendVerification(user)
        return RegistrationResult(user.email)
    }

    /** Mails a fresh verification link, unless the address is unknown, verified or was just mailed. */
    fun resendVerification(email: String) {
        val user = users.findByEmail(canonicalEmail(email)) ?: return
        if (user.emailVerifiedAt == null) sendVerification(user)
    }

    /** Confirms the address and signs the user in. */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun verifyEmail(rawToken: String): AuthTokens {
        val user = consume(rawToken, EmailTokenPurpose.VERIFY_EMAIL)
        if (user.emailVerifiedAt == null) {
            user.emailVerifiedAt = Instant.now()
            users.save(user)
        }
        return authService.startSession(user)
    }

    /** Mails a reset link, unless the address is unknown or was just mailed one. */
    fun requestPasswordReset(email: String) {
        val user = users.findByEmail(canonicalEmail(email)) ?: return
        issue(user, EmailTokenPurpose.RESET_PASSWORD, properties.passwordResetTokenTtl)?.let { token ->
            outbox.record(PasswordResetPayload(user.email, user.displayName, token))
        }
    }

    /** Sets a new password and ends every session; the user then signs in with it. */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun resetPassword(
        rawToken: String,
        newPassword: String,
    ) {
        val user = consume(rawToken, EmailTokenPurpose.RESET_PASSWORD)
        user.passwordHash = encode(newPassword)
        // Using the mailed link proves the address works, so it no longer needs confirming.
        user.emailVerifiedAt = user.emailVerifiedAt ?: Instant.now()
        users.save(user)
        refreshTokens.deleteAllByUserId(user.id)
        loginThrottle.reset(user.email)
    }

    /** Changes the password, ends every other session and returns a new one for the caller. */
    fun changePassword(
        userId: UUID,
        currentPassword: String,
        newPassword: String,
    ): AuthTokens {
        val user = users.findById(userId) ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)
        passwordConfirmation.require(user, currentPassword)
        user.passwordHash = encode(newPassword)
        users.save(user)
        refreshTokens.deleteAllByUserId(user.id)
        return authService.startSession(user)
    }

    private fun sendVerification(user: User) {
        issue(user, EmailTokenPurpose.VERIFY_EMAIL, properties.verificationTokenTtl)?.let { token ->
            outbox.record(EmailVerificationPayload(user.email, user.displayName, token))
        }
    }

    /** Replaces the user's token for [purpose]; `null` (nothing mailed) within the cooldown. */
    private fun issue(
        user: User,
        purpose: EmailTokenPurpose,
        ttl: Duration,
    ): String? {
        val now = Instant.now()
        val previous = emailTokens.findByUser(user.id, purpose)
        if (previous != null && previous.createdAt.plus(properties.emailTokenCooldown).isAfter(now)) return null
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))
        emailTokens.replace(
            EmailToken(user = user, purpose = purpose, tokenHash = hash(raw), expiresAt = now.plus(ttl))
        )
        return raw
    }

    private fun consume(
        rawToken: String,
        purpose: EmailTokenPurpose,
    ): User {
        val token =
            emailTokens.findByTokenHash(hash(rawToken), purpose)
                ?: throw BadRequestException("This link is invalid or has already been used", ErrorCode.INVALID_TOKEN)
        emailTokens.delete(token)
        if (token.isExpired) throw BadRequestException("This link has expired", ErrorCode.TOKEN_EXPIRED)
        return token.user
    }

    private fun encode(password: String) = checkNotNull(passwordEncoder.encode(password)) { "Password encoding failed" }

    private fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
