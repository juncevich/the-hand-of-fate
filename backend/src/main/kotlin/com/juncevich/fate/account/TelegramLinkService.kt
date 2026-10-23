package com.juncevich.fate.account

import com.juncevich.fate.account.domain.TelegramLinkToken
import com.juncevich.fate.account.port.TelegramLinkTokenRepositoryPort
import com.juncevich.fate.account.port.UserRepositoryPort
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.ensureState
import com.juncevich.fate.shared.ensureValid
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
                ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)
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
        // Group and channel chat ids are negative: an account may only be bound to a person.
        ensureValid(telegramId > 0, ErrorCode.TELEGRAM_PRIVATE_CHAT_ONLY) {
            "Telegram account can only be linked from a private chat"
        }
        val linkToken =
            linkTokenRepositoryPort.findByToken(token)
                ?: throw NotFoundException("Invalid or expired link token", ErrorCode.TELEGRAM_LINK_TOKEN_INVALID)

        if (linkToken.isExpired) {
            linkTokenRepositoryPort.delete(linkToken)
            throw BadRequestException(
                "Link token has expired. Please generate a new one from the app.",
                ErrorCode.TELEGRAM_LINK_TOKEN_EXPIRED
            )
        }

        userRepositoryPort.findByTelegramId(telegramId)?.let { existing ->
            ensureState(existing.id == linkToken.user.id, ErrorCode.TELEGRAM_ALREADY_LINKED) {
                "This Telegram account is already linked to another user"
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
        val user =
            userRepositoryPort.findByTelegramId(telegramId)
                ?: throw NotFoundException("Telegram account not linked to any user", ErrorCode.TELEGRAM_NOT_LINKED)
        user.telegramId = null
        user.telegramName = null
        userRepositoryPort.save(user)
    }

    fun unlinkByUserId(userId: UUID) {
        val user =
            userRepositoryPort.findById(userId)
                ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)
        ensureState(
            user.telegramId != null,
            ErrorCode.TELEGRAM_NOT_LINKED
        ) { "No Telegram account is linked to this user" }
        user.telegramId = null
        user.telegramName = null
        userRepositoryPort.save(user)
    }
}
