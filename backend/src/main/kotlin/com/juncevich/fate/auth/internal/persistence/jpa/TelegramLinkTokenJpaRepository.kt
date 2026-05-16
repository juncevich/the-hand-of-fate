package com.juncevich.fate.auth.internal.persistence.jpa

import com.juncevich.fate.auth.internal.persistence.entity.TelegramLinkTokenJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface TelegramLinkTokenJpaRepository : JpaRepository<TelegramLinkTokenJpaEntity, UUID> {
    fun findByToken(token: String): TelegramLinkTokenJpaEntity?

    @Modifying
    @Query("DELETE FROM TelegramLinkTokenJpaEntity t WHERE t.user.id = :userId")
    fun deleteAllByUserId(userId: UUID)
}
