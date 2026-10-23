package com.juncevich.fate.account.persistence

import jakarta.persistence.*
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "deleted_accounts")
class DeletedAccountJpaEntity(
    @Id
    @Column(name = "user_id")
    var userId: UUID,
    @Column(name = "deleted_at", nullable = false, updatable = false)
    var deletedAt: Instant,
    @Column(name = "identity_deleted_at")
    var identityDeletedAt: Instant? = null,
)

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
