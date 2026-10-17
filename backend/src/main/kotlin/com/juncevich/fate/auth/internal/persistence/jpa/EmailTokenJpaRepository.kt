package com.juncevich.fate.auth.internal.persistence.jpa

import com.juncevich.fate.auth.internal.domain.EmailTokenPurpose
import com.juncevich.fate.auth.internal.persistence.entity.EmailTokenJpaEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface EmailTokenJpaRepository : JpaRepository<EmailTokenJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByTokenHashAndPurpose(
        tokenHash: String,
        purpose: EmailTokenPurpose,
    ): EmailTokenJpaEntity?

    fun findByUserIdAndPurpose(
        userId: UUID,
        purpose: EmailTokenPurpose,
    ): EmailTokenJpaEntity?

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM EmailTokenJpaEntity t WHERE t.user.id = :userId AND t.purpose = :purpose")
    fun deleteByUserIdAndPurpose(
        userId: UUID,
        purpose: EmailTokenPurpose,
    )

    @Modifying
    @Query("DELETE FROM EmailTokenJpaEntity t WHERE t.expiresAt < :now")
    fun deleteExpired(now: Instant): Int
}
