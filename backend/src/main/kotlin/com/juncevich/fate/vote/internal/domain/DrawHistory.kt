package com.juncevich.fate.vote.internal.domain

import java.time.Instant
import java.util.UUID

class DrawHistory(
    val id: UUID = UUID.randomUUID(),
    val voteId: UUID,
    val winnerEmail: String? = null,
    val winnerDisplayName: String? = null,
    val winnerOptionId: UUID? = null,
    val winnerOptionTitle: String? = null,
    val round: Int,
    val drawnAt: Instant = Instant.now(),
)
