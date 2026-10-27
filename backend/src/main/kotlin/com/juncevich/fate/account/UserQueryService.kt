package com.juncevich.fate.account

import com.juncevich.fate.account.domain.UserRepository
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import org.springframework.stereotype.Service

@Service
class UserQueryService(
    private val userRepository: UserRepository,
) {
    fun findProfileById(id: UserId): UserProfile? = userRepository.findById(id)?.toProfile()

    fun findProfileByEmail(email: Email): UserProfile? = userRepository.findByEmail(email)?.toProfile()

    /** The account the Telegram account [telegramId] is linked to. */
    fun findProfileByTelegramId(telegramId: Long): UserProfile? =
        userRepository.findByTelegramId(telegramId)?.toProfile()

    fun findProfilesByIdIn(ids: Collection<UserId>): List<UserProfile> =
        userRepository.findAllByIdIn(ids).map { it.toProfile() }

    fun findProfilesByEmailIn(emails: Collection<Email>): List<UserProfile> =
        userRepository.findAllByEmailIn(emails).map { it.toProfile() }
}
