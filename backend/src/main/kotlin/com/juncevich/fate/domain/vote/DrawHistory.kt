package com.juncevich.fate.domain.vote

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "draw_history")
class DrawHistory(

    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_id", nullable = false)
    val vote: Vote,

    @Column(name = "winner_email", length = 255)
    val winnerEmail: String? = null,

    @Column(name = "winner_display_name", length = 100)
    val winnerDisplayName: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_option_id")
    val winnerOption: VoteOption? = null,

    @Column(name = "winner_option_title", length = 255)
    val winnerOptionTitle: String? = null,

    @Column(nullable = false)
    val round: Int,

    @Column(name = "drawn_at", nullable = false)
    val drawnAt: Instant = Instant.now(),
)
