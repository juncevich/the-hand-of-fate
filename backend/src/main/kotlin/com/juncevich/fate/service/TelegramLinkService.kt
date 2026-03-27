package com.juncevich.fate.service

import com.juncevich.fate.domain.auth.TelegramLinkToken
import com.juncevich.fate.domain.auth.TelegramLinkTokenRepository
import com.juncevich.fate.domain.user.User
import com.juncevich.fate.domain.user.UserRepository
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
        // Revoke any existing link tokens for this user
        linkTokenRepository.deleteAllByUserId(userId)

        val token = UUID.randomUUID().toString().replace("-", "")
        val user = userRepository.findById(userId).orElseThrow()

        linkTokenRepository.save(
            TelegramLinkToken(
                user = user,
                token = token,
                expiresAt = Instant.now().plusSeconds(5 * 60), // 5 minutes
            )
        )
        return token
    }

    fun linkAccount(token: String, telegramId: Long, telegramName: String): User {
        val linkToken = linkTokenRepository.findByToken(token)
            ?: error("Invalid or expired link token")

        if (linkToken.isExpired) {
            linkTokenRepository.delete(linkToken)
            error("Link token has expired. Please generate a new one from the app.")
        }

        // Check if another user already has this telegram_id
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
        val user = userRepository.findByTelegramId(telegramId)
            ?: error("Telegram account not linked to any user")
        user.telegramId = null
        user.telegramName = null
        userRepository.save(user)
    }
}
