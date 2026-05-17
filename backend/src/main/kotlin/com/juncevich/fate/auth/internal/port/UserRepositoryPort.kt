package com.juncevich.fate.auth.internal.port

import com.juncevich.fate.auth.User
import java.util.UUID

interface UserRepositoryPort {
    fun findById(id: UUID): User?

    fun findByEmail(email: String): User?

    fun findByTelegramId(telegramId: Long): User?

    fun findAllByEmailIn(emails: Collection<String>): List<User>

    fun findAllByIdIn(ids: Collection<UUID>): List<User>

    fun existsByEmail(email: String): Boolean

    fun save(user: User): User
}
