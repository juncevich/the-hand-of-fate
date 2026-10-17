package com.juncevich.fate.auth.internal.persistence.entity

import com.juncevich.fate.auth.internal.domain.EmailTokenPurpose
import com.juncevich.fate.shared.uuidV7
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "email_tokens")
class EmailTokenJpaEntity(
    @Id
    val id: UUID = uuidV7(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserJpaEntity,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val purpose: EmailTokenPurpose,
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
