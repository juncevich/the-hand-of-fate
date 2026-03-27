package com.juncevich.fate.service

import com.juncevich.fate.config.JwtProperties
import com.juncevich.fate.domain.auth.RefreshToken
import com.juncevich.fate.domain.auth.RefreshTokenRepository
import com.juncevich.fate.domain.user.User
import com.juncevich.fate.domain.user.UserRepository
import com.juncevich.fate.security.JwtTokenProvider
import com.juncevich.fate.web.auth.AuthResponse
import com.juncevich.fate.web.auth.RegisterRequest
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

    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            error("Email already registered")
        }
        val user = userRepository.save(
            User(
                email = request.email.lowercase().trim(),
                passwordHash = passwordEncoder.encode(request.password),
                displayName = request.displayName,
            )
        )
        return issueTokens(user)
    }

    fun login(email: String, password: String): AuthResponse {
        val user = userRepository.findByEmail(email.lowercase().trim())
            ?: error("Invalid credentials")
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            error("Invalid credentials")
        }
        return issueTokens(user)
    }

    fun refresh(rawRefreshToken: String): AuthResponse {
        val hash = hashToken(rawRefreshToken)
        val stored = refreshTokenRepository.findByTokenHash(hash)
            ?: error("Refresh token not found")
        if (stored.isExpired) {
            refreshTokenRepository.delete(stored)
            error("Refresh token expired")
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

    private fun issueTokens(user: User): AuthResponse {
        val accessToken = jwtTokenProvider.createAccessToken(user.id, user.email)
        val rawRefresh = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTtlDays * 24 * 3600)

        refreshTokenRepository.save(
            RefreshToken(
                user = user,
                tokenHash = hashToken(rawRefresh),
                expiresAt = expiresAt,
            )
        )
        return AuthResponse(
            accessToken = accessToken,
            refreshToken = rawRefresh,
            userId = user.id.toString(),
            email = user.email,
            displayName = user.displayName,
        )
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(token.toByteArray()))
    }
}
