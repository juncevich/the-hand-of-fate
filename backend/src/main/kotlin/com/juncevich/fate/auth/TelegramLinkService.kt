package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.domain.TelegramLinkToken
import com.juncevich.fate.auth.internal.port.TelegramLinkTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private const val LINK_TOKEN_TTL_SECONDS = 5L * 60

data class GeneratedLinkToken(
    val token: String,
    val expiresAt: Instant,
)

@Service
@Transactional
class TelegramLinkService(
    private val linkTokenRepositoryPort: TelegramLinkTokenRepositoryPort,
    private val userRepositoryPort: UserRepositoryPort,
) {
    fun generateLinkToken(userId: UUID): GeneratedLinkToken {
        linkTokenRepositoryPort.deleteAllByUserId(userId)

        val token = UUID.randomUUID().toString().replace("-", "")
        val user =
            userRepositoryPort.findById(userId)
                ?: throw NoSuchElementException("User not found")
        val expiresAt = Instant.now().plusSeconds(LINK_TOKEN_TTL_SECONDS)

        linkTokenRepositoryPort.save(
            TelegramLinkToken(
                user = user,
                token = token,
                expiresAt = expiresAt
            )
        )
        return GeneratedLinkToken(token = token, expiresAt = expiresAt)
    }

    fun linkAccount(
        token: String,
        telegramId: Long,
        telegramName: String,
    ): User {
        val linkToken =
            linkTokenRepositoryPort.findByToken(token)
                ?: throw NoSuchElementException("Invalid or expired link token")

        if (linkToken.isExpired) {
            linkTokenRepositoryPort.delete(linkToken)
            error("Link token has expired. Please generate a new one from the app.")
        }

        userRepositoryPort.findByTelegramId(telegramId)?.let { existing ->
            check(existing.id == linkToken.user.id) { "This Telegram account is already linked to another user" }
        }

        val user = linkToken.user
        user.telegramId = telegramId
        user.telegramName = telegramName
        val savedUser = userRepositoryPort.save(user)

        linkTokenRepositoryPort.delete(linkToken)
        return savedUser
    }

    fun unlinkAccount(telegramId: Long) {
        val user =
            userRepositoryPort.findByTelegramId(telegramId)
                ?: throw NoSuchElementException("Telegram account not linked to any user")
        user.telegramId = null
        user.telegramName = null
        userRepositoryPort.save(user)
    }

    fun unlinkByUserId(userId: UUID) {
        val user =
            userRepositoryPort.findById(userId)
                ?: throw NoSuchElementException("User not found")
        check(user.telegramId != null) { "No Telegram account is linked to this user" }
        user.telegramId = null
        user.telegramName = null
        userRepositoryPort.save(user)
    }
}
