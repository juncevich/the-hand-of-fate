package com.juncevich.fate.auth.internal.persistence.entity

import com.juncevich.fate.shared.uuidV7
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

// One constructor parameter per column of `users`; grouping them would only obscure the mapping.
@Suppress("LongParameterList")
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener::class)
class UserJpaEntity(
    @Id
    var id: UUID = uuidV7(),
    @Column(nullable = false, unique = true, length = 255)
    var email: String,
    @Column(name = "display_name", nullable = false, length = 100)
    var displayName: String,
    @Column(name = "telegram_id", unique = true)
    var telegramId: Long? = null,
    @Column(name = "telegram_name", length = 100)
    var telegramName: String? = null,
    @Column(name = "avatar_updated_at")
    var avatarUpdatedAt: Instant? = null,
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
