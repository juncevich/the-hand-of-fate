package com.juncevich.fate.vote.internal.domain

import com.juncevich.fate.auth.User
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "votes")
@EntityListeners(AuditingEntityListener::class)
class Vote(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 255)
    var title: String,
    @Column(columnDefinition = "TEXT")
    var description: String? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    val creator: User,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var mode: VoteMode = VoteMode.SIMPLE,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: VoteStatus = VoteStatus.PENDING,
    @Column(name = "current_round", nullable = false)
    var currentRound: Int = 1,
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
