package com.juncevich.fate.vote.internal.persistence.entity

import com.juncevich.fate.shared.uuidV7
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "vote_participants",
    uniqueConstraints = [UniqueConstraint(columnNames = ["vote_id", "email"])]
)
@EntityListeners(AuditingEntityListener::class)
class VoteParticipantJpaEntity(
    @Id
    val id: UUID = uuidV7(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_id", nullable = false)
    val vote: VoteJpaEntity,
    @Column(nullable = false, length = 255)
    val email: String,
    @Column(name = "display_name", length = 100)
    var displayName: String? = null,
    @CreatedDate
    @Column(name = "added_at", nullable = false, updatable = false)
    var addedAt: Instant = Instant.now(),
)
