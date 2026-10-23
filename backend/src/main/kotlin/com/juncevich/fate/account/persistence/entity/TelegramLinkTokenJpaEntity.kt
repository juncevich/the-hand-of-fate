package com.juncevich.fate.account.persistence.entity

import com.juncevich.fate.shared.uuidV7
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "telegram_link_tokens")
class TelegramLinkTokenJpaEntity(
    @Id
    var id: UUID = uuidV7(),
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserJpaEntity,
    @Column(nullable = false, unique = true, length = 64)
    var token: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)
