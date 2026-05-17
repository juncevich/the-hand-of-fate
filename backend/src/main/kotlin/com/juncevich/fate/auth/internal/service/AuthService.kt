package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.domain.RefreshToken
import com.juncevich.fate.auth.internal.port.RefreshTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.auth.internal.token.JwtProperties
import com.juncevich.fate.auth.internal.token.JwtTokenProvider
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
) {
    fun register(request: RegisterRequest): AuthTokens {
        check(!userRepositoryPort.existsByEmail(request.email)) { "Email already registered" }
        val user =
            userRepositoryPort.save(
                User(
                    email = request.email.lowercase().trim(),
                    passwordHash =
                        requireNotNull(passwordEncoder.encode(request.password)) {
                            "Password encoding failed"
                        },
                    displayName = request.displayName
                )
            )
        return issueTokens(user)
    }

    fun login(
        email: String,
        password: String,
    ): AuthTokens {
        val user =
            userRepositoryPort.findByEmail(email.lowercase().trim())
                ?: throw BadCredentialsException("Invalid credentials")
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw BadCredentialsException("Invalid credentials")
        }
        return issueTokens(user)
    }

    fun refresh(rawRefreshToken: String): AuthTokens {
        val hash = hashToken(rawRefreshToken)
        val stored =
            refreshTokenRepositoryPort.findByTokenHash(hash)
                ?: throw BadCredentialsException("Refresh token not found")
        if (stored.isExpired) {
            refreshTokenRepositoryPort.delete(stored)
            throw BadCredentialsException("Refresh token expired")
        }
        refreshTokenRepositoryPort.delete(stored)
        return issueTokens(stored.user)
    }

    fun logout(rawRefreshToken: String) {
        val hash = hashToken(rawRefreshToken)
        refreshTokenRepositoryPort.findByTokenHash(hash)?.let {
            refreshTokenRepositoryPort.delete(it)
        }
    }

    fun logoutAll(userId: UUID) {
        refreshTokenRepositoryPort.deleteAllByUserId(userId)
    }

    private fun issueTokens(user: User): AuthTokens {
        val accessToken = jwtTokenProvider.createAccessToken(user.id, user.email)
        val rawRefresh = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTtlDays * SECONDS_PER_DAY)

        refreshTokenRepositoryPort.save(
            RefreshToken(
                user = user,
                tokenHash = hashToken(rawRefresh),
                expiresAt = expiresAt
            )
        )
        return AuthTokens(
            response =
                AuthResponse(
                    accessToken = accessToken,
                    userId = user.id.toString(),
                    email = user.email,
                    displayName = user.displayName
                ),
            refreshToken = rawRefresh
        )
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(token.toByteArray()))
    }
}
