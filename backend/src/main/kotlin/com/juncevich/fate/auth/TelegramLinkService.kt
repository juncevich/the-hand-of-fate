package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.domain.TelegramLinkToken
import com.juncevich.fate.auth.internal.port.TelegramLinkTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.ensureState
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
                ?: throw NotFoundException("User not found")
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

    @Transactional(noRollbackFor = [BadRequestException::class])
    fun linkAccount(
        token: String,
        telegramId: Long,
        telegramName: String,
    ): User {
        val linkToken =
            linkTokenRepositoryPort.findByToken(token)
                ?: throw NotFoundException("Invalid or expired link token")

        if (linkToken.isExpired) {
            linkTokenRepositoryPort.delete(linkToken)
            throw BadRequestException("Link token has expired. Please generate a new one from the app.")
        }

        userRepositoryPort.findByTelegramId(telegramId)?.let { existing ->
            ensureState(existing.id == linkToken.user.id) { "This Telegram account is already linked to another user" }
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
                ?: throw NotFoundException("Telegram account not linked to any user")
        user.telegramId = null
        user.telegramName = null
        userRepositoryPort.save(user)
    }

    fun unlinkByUserId(userId: UUID) {
        val user =
            userRepositoryPort.findById(userId)
                ?: throw NotFoundException("User not found")
        ensureState(user.telegramId != null) { "No Telegram account is linked to this user" }
        user.telegramId = null
        user.telegramName = null
        userRepositoryPort.save(user)
    }
}
