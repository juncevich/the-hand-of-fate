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

    /** Gives the account the id [to]; references follow it. False when [from] does not exist. */
    fun changeId(
        from: UUID,
        to: UUID,
    ): Boolean

    /** Inserts [user] unless an account with its id or email exists; true when it was inserted. */
    fun insertIfAbsent(user: User): Boolean

    fun delete(id: UUID)
}
