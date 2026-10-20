package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.domain.RefreshToken
import com.juncevich.fate.auth.internal.port.IdentityAdminPort
import com.juncevich.fate.auth.internal.port.RefreshTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.auth.internal.token.JwtProperties
import com.juncevich.fate.auth.internal.token.JwtTokenProvider
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.canonicalEmail
import com.juncevich.fate.shared.uuidV7
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val SECONDS_PER_DAY = 24L * 3600

@Service
@Transactional
class AuthService(
    private val userRepositoryPort: UserRepositoryPort,
    private val refreshTokenRepositoryPort: RefreshTokenRepositoryPort,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
    private val loginThrottle: LoginThrottle,
    private val identityAdmin: IdentityAdminPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Precomputed once so the login() timing-mitigation branch doesn't hash a fresh
    // random string (and thus a fresh salt) on every unknown-email attempt.
    private val dummyPasswordHash: String by lazy {
        checkNotNull(passwordEncoder.encode(UUID.randomUUID().toString())) { "Password encoding failed" }
    }

    fun login(
        email: String,
        password: String,
    ): AuthTokens {
        // Not normalizeEmail(): a malformed address must fail like any wrong credentials.
        val normalizedEmail = canonicalEmail(email)
        loginThrottle.ensureAllowed(normalizedEmail)
        val user = userRepositoryPort.findByEmail(normalizedEmail)
        // Always run the hash comparison, even for an unknown email, so response
        // time doesn't leak whether the address is registered (timing side-channel).
        val matches = passwordEncoder.matches(password, user?.passwordHash ?: dummyPasswordHash)
        if (user == null || !matches) {
            loginThrottle.recordFailure(normalizedEmail)
            throw BadCredentialsException("Invalid credentials")
        }
        loginThrottle.reset(normalizedEmail)
        // Only after the password matched, so the answer reveals nothing to someone guessing.
        if (user.emailVerifiedAt == null) {
            throw ForbiddenException(
                "Confirm your email address first — we sent you a link",
                ErrorCode.EMAIL_NOT_VERIFIED
            )
        }
        return issueTokens(user)
    }

    @Transactional(noRollbackFor = [BadCredentialsException::class])
    fun refresh(rawRefreshToken: String): AuthTokens {
        val stored = findUsableRefreshToken(rawRefreshToken)
        rotate(stored)
        return issueTokens(stored.user, stored.familyId)
    }

    fun logout(rawRefreshToken: String) {
        val hash = hashToken(rawRefreshToken)
        refreshTokenRepositoryPort.findByTokenHash(hash)?.let {
            refreshTokenRepositoryPort.deleteFamily(it.familyId)
        }
    }

    /** Signs [user] in: a new session family with fresh access and refresh tokens. */
    fun startSession(user: User): AuthTokens = issueTokens(user)

    /** Ends the user's sessions here and in Keycloak; access tokens already issued live until they expire. */
    fun logoutAll(userId: UUID) {
        refreshTokenRepositoryPort.deleteAllByUserId(userId)
        identityAdmin.logoutUser(userId)
    }

    /** Starts a new session, or continues the session [familyId] when rotating. */
    private fun issueTokens(
        user: User,
        familyId: UUID? = null,
    ): AuthTokens {
        val accessToken = jwtTokenProvider.createAccessToken(user.id, user.email)
        val rawRefresh = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTtlDays * SECONDS_PER_DAY)

        val tokenId = uuidV7()
        refreshTokenRepositoryPort.save(
            RefreshToken(
                id = tokenId,
                user = user,
                tokenHash = hashToken(rawRefresh),
                expiresAt = expiresAt,
                familyId = familyId ?: tokenId
            )
        )
        return AuthTokens(
            accessToken = accessToken,
            refreshToken = rawRefresh,
            userId = user.id,
            email = user.email,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl()
        )
    }

    private fun findUsableRefreshToken(rawRefreshToken: String): RefreshToken {
        val stored =
            refreshTokenRepositoryPort.findByTokenHash(hashToken(rawRefreshToken))
                ?: throw BadCredentialsException("Refresh token not found")
        if (stored.isExpired) {
            refreshTokenRepositoryPort.delete(stored)
            throw BadCredentialsException("Refresh token expired")
        }
        return stored
    }

    /** Retires [stored] in favour of its successor, or ends its session if it was retired long ago. */
    private fun rotate(stored: RefreshToken) {
        val now = Instant.now()
        val revokedAt = stored.revokedAt ?: return refreshTokenRepositoryPort.revoke(stored, now)
        if (now.isAfter(revokedAt.plusSeconds(jwtProperties.refreshReuseGraceSeconds))) {
            // Its successor has long been issued, so this copy is in someone else's hands.
            log.warn("Rotated refresh token replayed; revoking session {} of user {}", stored.familyId, stored.user.id)
            refreshTokenRepositoryPort.deleteFamily(stored.familyId)
            throw BadCredentialsException("Refresh token reuse detected")
        }
        // Otherwise a concurrent refresh lost the race by moments: serve it within the same session.
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(token.toByteArray()))
    }
}
