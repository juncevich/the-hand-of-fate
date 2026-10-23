package com.juncevich.fate.account

import com.juncevich.fate.account.domain.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserQueryService(
    private val userRepository: UserRepository,
) {
    fun findProfileById(id: UUID): UserProfile? = userRepository.findById(id)?.toProfile()

    fun findProfileByEmail(email: String): UserProfile? = userRepository.findByEmail(email)?.toProfile()

    /** The account the Telegram account [telegramId] is linked to. */
    fun findProfileByTelegramId(telegramId: Long): UserProfile? =
        userRepository.findByTelegramId(telegramId)?.toProfile()

    fun findProfilesByIdIn(ids: Collection<UUID>): List<UserProfile> =
        userRepository.findAllByIdIn(ids).map {
            it.toProfile()
        }

    fun findProfilesByEmailIn(emails: Collection<String>): List<UserProfile> =
        userRepository.findAllByEmailIn(emails).map {
            it.toProfile()
        }
}
