package com.juncevich.fate.vote.persistence.entity

import com.juncevich.fate.shared.uuidV7
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "vote_options",
    uniqueConstraints = [UniqueConstraint(columnNames = ["vote_id", "title"])]
)
@EntityListeners(AuditingEntityListener::class)
class VoteOptionJpaEntity(
    @Id
    var id: UUID = uuidV7(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_id", nullable = false)
    var vote: VoteJpaEntity,
    @Column(nullable = false, length = 255)
    var title: String,
    @Column(nullable = false)
    var position: Int = 0,
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)
