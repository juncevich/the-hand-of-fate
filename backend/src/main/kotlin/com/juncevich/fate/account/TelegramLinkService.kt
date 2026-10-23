package com.juncevich.fate.account

import com.juncevich.fate.account.domain.TelegramLinkToken
import com.juncevich.fate.account.domain.TelegramLinkTokenRepository
import com.juncevich.fate.account.domain.UserRepository
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
    private val linkTokens: TelegramLinkTokenRepository,
    private val userRepository: UserRepository,
) {
    fun generateLinkToken(userId: UUID): GeneratedLinkToken {
        linkTokens.deleteAllByUserId(userId)

        val token = UUID.randomUUID().toString().replace("-", "")
        userRepository.findById(userId) ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)
        val expiresAt = Instant.now().plusSeconds(LINK_TOKEN_TTL_SECONDS)

        linkTokens.insert(TelegramLinkToken(userId = userId, token = token, expiresAt = expiresAt))
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
            linkTokens.findByToken(token)
                ?: throw NotFoundException("Invalid or expired link token", ErrorCode.TELEGRAM_LINK_TOKEN_INVALID)

        if (linkToken.isExpired) {
            linkTokens.delete(linkToken)
            throw BadRequestException(
                "Link token has expired. Please generate a new one from the app.",
                ErrorCode.TELEGRAM_LINK_TOKEN_EXPIRED
            )
        }

        userRepository.findByTelegramId(telegramId)?.let { existing ->
            ensureState(existing.id == linkToken.userId, ErrorCode.TELEGRAM_ALREADY_LINKED) {
                "This Telegram account is already linked to another user"
            }
        }

        // The token's account always exists: its row cascades the token away (telegram_link_tokens.user_id).
        val user =
            checkNotNull(userRepository.findById(linkToken.userId)) { "User ${linkToken.userId} not found" }
                .linkTelegram(telegramId, telegramName)
        userRepository.update(user)

        linkTokens.delete(linkToken)
        return user
    }

    fun unlinkAccount(telegramId: Long) {
        val user =
            userRepository.findByTelegramId(telegramId)
                ?: throw NotFoundException("Telegram account not linked to any user", ErrorCode.TELEGRAM_NOT_LINKED)
        userRepository.update(user.unlinkTelegram())
    }

    fun unlinkByUserId(userId: UUID) {
        val user =
            userRepository.findById(userId)
                ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)
        ensureState(
            user.isTelegramLinked,
            ErrorCode.TELEGRAM_NOT_LINKED
        ) { "No Telegram account is linked to this user" }
        userRepository.update(user.unlinkTelegram())
    }
}
