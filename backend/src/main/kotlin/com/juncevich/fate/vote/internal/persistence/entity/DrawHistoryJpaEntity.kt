package com.juncevich.fate.vote.internal.persistence.entity

import com.juncevich.fate.shared.uuidV7
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "draw_history")
class DrawHistoryJpaEntity(
    @Id
    var id: UUID = uuidV7(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_id", nullable = false)
    var vote: VoteJpaEntity,
    @Column(name = "winner_email", length = 255)
    var winnerEmail: String? = null,
    @Column(name = "winner_display_name", length = 100)
    var winnerDisplayName: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_option_id")
    var winnerOption: VoteOptionJpaEntity? = null,
    @Column(name = "winner_option_title", length = 255)
    var winnerOptionTitle: String? = null,
    @Column(nullable = false)
    var round: Int,
    @Column(name = "drawn_at", nullable = false)
    var drawnAt: Instant = Instant.now(),
    /** `null` for draws recorded before V22 (both columns NULL). */
    @Embedded
    var audit: DrawAuditColumns? = null,
)

@Embeddable
class DrawAuditColumns(
    @Column(name = "seed", length = 64, updatable = false)
    var seed: String,
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "candidates", columnDefinition = "TEXT[]", updatable = false)
    var candidates: List<String>,
)
