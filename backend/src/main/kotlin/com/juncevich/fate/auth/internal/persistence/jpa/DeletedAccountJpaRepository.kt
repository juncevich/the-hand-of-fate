package com.juncevich.fate.auth.internal.persistence.jpa

import com.juncevich.fate.auth.internal.persistence.entity.DeletedAccountJpaEntity
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface DeletedAccountJpaRepository : JpaRepository<DeletedAccountJpaEntity, UUID> {
    @Query(
        "SELECT d.userId FROM DeletedAccountJpaEntity d WHERE d.identityDeletedAt IS NULL ORDER BY d.deletedAt"
    )
    fun findPendingIdentityDeletion(limit: Limit): List<UUID>

    @Transactional
    @Modifying
    @Query("UPDATE DeletedAccountJpaEntity d SET d.identityDeletedAt = :at WHERE d.userId = :userId")
    fun markIdentityDeleted(
        userId: UUID,
        at: Instant,
    )

    @Transactional
    @Modifying
    @Query(
        "DELETE FROM DeletedAccountJpaEntity d WHERE d.deletedAt < :before AND d.identityDeletedAt IS NOT NULL"
    )
    fun purge(before: Instant): Int
}
