package com.juncevich.fate.auth.internal.persistence.jpa

import com.juncevich.fate.auth.internal.persistence.entity.RefreshTokenJpaEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByTokenHash(tokenHash: String): RefreshTokenJpaEntity?

    @Modifying
    @Query("DELETE FROM RefreshTokenJpaEntity r WHERE r.user.id = :userId")
    fun deleteAllByUserId(userId: UUID)

    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :at WHERE r.id = :id")
    fun revoke(
        id: UUID,
        at: Instant,
    )

    @Modifying
    @Query("DELETE FROM RefreshTokenJpaEntity r WHERE r.familyId = :familyId")
    fun deleteAllByFamilyId(familyId: UUID)

    @Modifying
    @Query("DELETE FROM RefreshTokenJpaEntity r WHERE r.expiresAt < :now")
    fun deleteExpired(now: Instant): Int
}
