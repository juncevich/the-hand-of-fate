package com.juncevich.fate.auth.internal.persistence

import com.juncevich.fate.auth.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?

    fun findByTelegramId(telegramId: Long): User?

    fun existsByEmail(email: String): Boolean

    fun findAllByEmailIn(emails: Collection<String>): List<User>
}
