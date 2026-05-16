package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.persistence.RefreshToken
import com.juncevich.fate.auth.internal.persistence.RefreshTokenRepository
import com.juncevich.fate.auth.internal.persistence.UserRepository
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

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
) {
    fun register(request: RegisterRequest): AuthTokens {
        if (userRepository.existsByEmail(request.email)) {
            error("Email already registered")
        }
        val user =
            userRepository.save(
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
            userRepository.findByEmail(email.lowercase().trim())
                ?: throw BadCredentialsException("Invalid credentials")
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw BadCredentialsException("Invalid credentials")
        }
        return issueTokens(user)
    }

    fun refresh(rawRefreshToken: String): AuthTokens {
        val hash = hashToken(rawRefreshToken)
        val stored =
            refreshTokenRepository.findByTokenHash(hash)
                ?: throw BadCredentialsException("Refresh token not found")
        if (stored.isExpired) {
            refreshTokenRepository.delete(stored)
            throw BadCredentialsException("Refresh token expired")
        }
        refreshTokenRepository.delete(stored)
        return issueTokens(stored.user)
    }

    fun logout(rawRefreshToken: String) {
        val hash = hashToken(rawRefreshToken)
        refreshTokenRepository.findByTokenHash(hash)?.let {
            refreshTokenRepository.delete(it)
        }
    }

    fun logoutAll(userId: UUID) {
        refreshTokenRepository.deleteAllByUserId(userId)
    }

    private fun issueTokens(user: User): AuthTokens {
        val accessToken = jwtTokenProvider.createAccessToken(user.id, user.email)
        val rawRefresh = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTtlDays * 24 * 3600)

        refreshTokenRepository.save(
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
