package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.persistence.TelegramLinkToken
import com.juncevich.fate.auth.internal.persistence.TelegramLinkTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class TelegramLinkService(
    private val linkTokenRepository: TelegramLinkTokenRepository,
    private val userRepository: UserRepository,
) {
    fun generateLinkToken(userId: UUID): String {
        linkTokenRepository.deleteAllByUserId(userId)

        val token = UUID.randomUUID().toString().replace("-", "")
        val user = userRepository.findById(userId).orElseThrow()

        linkTokenRepository.save(
            TelegramLinkToken(
                user = user,
                token = token,
                expiresAt = Instant.now().plusSeconds(5 * 60)
            )
        )
        return token
    }

    fun linkAccount(
        token: String,
        telegramId: Long,
        telegramName: String,
    ): User {
        val linkToken =
            linkTokenRepository.findByToken(token)
                ?: error("Invalid or expired link token")

        if (linkToken.isExpired) {
            linkTokenRepository.delete(linkToken)
            error("Link token has expired. Please generate a new one from the app.")
        }

        userRepository.findByTelegramId(telegramId)?.let { existing ->
            if (existing.id != linkToken.user.id) {
                error("This Telegram account is already linked to another user")
            }
        }

        val user = linkToken.user
        user.telegramId = telegramId
        user.telegramName = telegramName
        userRepository.save(user)

        linkTokenRepository.delete(linkToken)
        return user
    }

    fun unlinkAccount(telegramId: Long) {
        val user =
            userRepository.findByTelegramId(telegramId)
                ?: error("Telegram account not linked to any user")
        user.telegramId = null
        user.telegramName = null
        userRepository.save(user)
    }

    fun unlinkByUserId(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        check(user.telegramId != null) { "No Telegram account is linked to this user" }
        user.telegramId = null
        user.telegramName = null
        userRepository.save(user)
    }
}
