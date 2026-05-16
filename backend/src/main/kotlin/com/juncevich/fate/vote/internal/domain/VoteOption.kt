package com.juncevich.fate.vote.internal.domain

import java.time.Instant
import java.util.UUID

class VoteOption(
    val id: UUID = UUID.randomUUID(),
    val voteId: UUID,
    val title: String,
    val position: Int = 0,
    val createdAt: Instant = Instant.now(),
)
