package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.domain.TelegramLinkToken
import com.juncevich.fate.auth.internal.port.TelegramLinkTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class TelegramLinkService(
    private val linkTokenRepositoryPort: TelegramLinkTokenRepositoryPort,
    private val userRepositoryPort: UserRepositoryPort,
) {
    fun generateLinkToken(userId: UUID): String {
        linkTokenRepositoryPort.deleteAllByUserId(userId)

        val token = UUID.randomUUID().toString().replace("-", "")
        val user = userRepositoryPort.findById(userId)
            ?: throw NoSuchElementException("User not found")

        linkTokenRepositoryPort.save(
            TelegramLinkToken(
                user = user,
                token = token,
                expiresAt = Instant.now().plusSeconds(5 * 60),
            )
        )
        return token
    }

    fun linkAccount(token: String, telegramId: Long, telegramName: String): User {
        val linkToken = linkTokenRepositoryPort.findByToken(token)
            ?: error("Invalid or expired link token")

        if (linkToken.isExpired) {
            linkTokenRepositoryPort.delete(linkToken)
            error("Link token has expired. Please generate a new one from the app.")
        }

        userRepositoryPort.findByTelegramId(telegramId)?.let { existing ->
            if (existing.id != linkToken.user.id) {
                error("This Telegram account is already linked to another user")
            }
        }

        val user = linkToken.user
        user.telegramId = telegramId
        user.telegramName = telegramName
        val savedUser = userRepositoryPort.save(user)

        linkTokenRepositoryPort.delete(linkToken)
        return savedUser
    }

    fun unlinkAccount(telegramId: Long) {
        val user = userRepositoryPort.findByTelegramId(telegramId)
            ?: error("Telegram account not linked to any user")
        user.telegramId = null
        user.telegramName = null
        userRepositoryPort.save(user)
    }

    fun unlinkByUserId(userId: UUID) {
        val user = userRepositoryPort.findById(userId)
            ?: throw NoSuchElementException("User not found")
        check(user.telegramId != null) { "No Telegram account is linked to this user" }
        user.telegramId = null
        user.telegramName = null
        userRepositoryPort.save(user)
    }
}
