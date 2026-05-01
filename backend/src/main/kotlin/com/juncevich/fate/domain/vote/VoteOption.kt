package com.juncevich.fate.domain.vote

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "vote_options",
    uniqueConstraints = [UniqueConstraint(columnNames = ["vote_id", "title"])],
)
@EntityListeners(AuditingEntityListener::class)
class VoteOption(

    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_id", nullable = false)
    val vote: Vote,

    @Column(nullable = false, length = 255)
    val title: String,

    @Column(nullable = false)
    val position: Int = 0,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)
