package com.juncevich.fate.auth.internal.persistence.jpa

import com.juncevich.fate.auth.internal.persistence.entity.UserJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByEmail(email: String): UserJpaEntity?
    fun findByTelegramId(telegramId: Long): UserJpaEntity?
    fun existsByEmail(email: String): Boolean
    fun findAllByEmailIn(emails: Collection<String>): List<UserJpaEntity>
}
