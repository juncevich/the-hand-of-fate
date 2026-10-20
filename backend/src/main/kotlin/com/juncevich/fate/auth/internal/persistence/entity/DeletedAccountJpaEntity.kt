package com.juncevich.fate.auth.internal.persistence.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "deleted_accounts")
class DeletedAccountJpaEntity(
    @Id
    @Column(name = "user_id")
    val userId: UUID,
    @Column(name = "deleted_at", nullable = false, updatable = false)
    val deletedAt: Instant,
    @Column(name = "identity_deleted_at")
    var identityDeletedAt: Instant? = null,
)
