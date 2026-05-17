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
}
