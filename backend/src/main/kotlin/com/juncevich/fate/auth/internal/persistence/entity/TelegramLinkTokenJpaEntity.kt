package com.juncevich.fate.auth.internal.persistence.entity

import com.juncevich.fate.shared.uuidV7
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "telegram_link_tokens")
class TelegramLinkTokenJpaEntity(
    @Id
    val id: UUID = uuidV7(),
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserJpaEntity,
    @Column(nullable = false, unique = true, length = 64)
    val token: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
