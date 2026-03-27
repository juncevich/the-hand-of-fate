package com.juncevich.fate.domain.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface TelegramLinkTokenRepository : JpaRepository<TelegramLinkToken, UUID> {
    fun findByToken(token: String): TelegramLinkToken?

    @Modifying
    @Query("DELETE FROM TelegramLinkToken t WHERE t.user.id = :userId")
    fun deleteAllByUserId(userId: UUID)

    @Modifying
    @Query("DELETE FROM TelegramLinkToken t WHERE t.expiresAt < :now")
    fun deleteAllExpiredBefore(now: Instant)
}
