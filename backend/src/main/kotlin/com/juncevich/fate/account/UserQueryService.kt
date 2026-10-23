package com.juncevich.fate.account

import com.juncevich.fate.account.domain.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserQueryService(
    private val userRepository: UserRepository,
) {
    fun findById(id: UUID): User? = userRepository.findById(id)

    fun findByEmail(email: String): User? = userRepository.findByEmail(email)

    fun findAllByEmailIn(emails: Collection<String>): List<User> = userRepository.findAllByEmailIn(emails)

    fun findAllByIdIn(ids: Collection<UUID>): List<User> = userRepository.findAllByIdIn(ids)

    fun findByTelegramId(telegramId: Long): User? = userRepository.findByTelegramId(telegramId)

    fun findProfileById(id: UUID): UserProfile? = userRepository.findById(id)?.toProfile()

    fun findProfileByEmail(email: String): UserProfile? = userRepository.findByEmail(email)?.toProfile()

    fun findProfilesByIdIn(ids: Collection<UUID>): List<UserProfile> =
        userRepository.findAllByIdIn(ids).map {
            it.toProfile()
        }

    fun findProfilesByEmailIn(emails: Collection<String>): List<UserProfile> =
        userRepository.findAllByEmailIn(emails).map {
            it.toProfile()
        }
}
