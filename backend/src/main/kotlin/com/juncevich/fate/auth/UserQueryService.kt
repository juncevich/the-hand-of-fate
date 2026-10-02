package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserQueryService(
    private val userRepositoryPort: UserRepositoryPort,
) {
    fun findById(id: UUID): User? = userRepositoryPort.findById(id)

    fun findByEmail(email: String): User? = userRepositoryPort.findByEmail(email)

    fun findAllByEmailIn(emails: Collection<String>): List<User> = userRepositoryPort.findAllByEmailIn(emails)

    fun findAllByIdIn(ids: Collection<UUID>): List<User> = userRepositoryPort.findAllByIdIn(ids)

    fun findByTelegramId(telegramId: Long): User? = userRepositoryPort.findByTelegramId(telegramId)

    fun findProfileById(id: UUID): UserProfile? = userRepositoryPort.findById(id)?.toProfile()

    fun findProfileByEmail(email: String): UserProfile? = userRepositoryPort.findByEmail(email)?.toProfile()

    fun findProfilesByIdIn(ids: Collection<UUID>): List<UserProfile> =
        userRepositoryPort.findAllByIdIn(ids).map {
            it.toProfile()
        }

    fun findProfilesByEmailIn(emails: Collection<String>): List<UserProfile> =
        userRepositoryPort.findAllByEmailIn(emails).map {
            it.toProfile()
        }
}
