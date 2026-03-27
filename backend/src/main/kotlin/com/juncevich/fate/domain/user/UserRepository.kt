package com.juncevich.fate.domain.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun findByTelegramId(telegramId: Long): User?
    fun existsByEmail(email: String): Boolean
}
