package com.juncevich.fate.account.persistence

import com.juncevich.fate.shared.uuidV7
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "telegram_link_tokens")
class TelegramLinkTokenJpaEntity(
    @Id
    var id: UUID = uuidV7(),
    // A plain id: a token needs its account only when it is presented. The FK cascades the deletion.
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(nullable = false, unique = true, length = 64)
    var token: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)

interface TelegramLinkTokenJpaRepository : JpaRepository<TelegramLinkTokenJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByToken(token: String): TelegramLinkTokenJpaEntity?

    @Modifying
    @Query("DELETE FROM TelegramLinkTokenJpaEntity t WHERE t.userId = :userId")
    fun deleteAllByUserId(userId: UUID)

    @Modifying
    @Query("DELETE FROM TelegramLinkTokenJpaEntity t WHERE t.expiresAt < :now")
    fun deleteExpired(now: Instant): Int
}
