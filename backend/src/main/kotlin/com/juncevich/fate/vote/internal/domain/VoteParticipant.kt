package com.juncevich.fate.vote.internal.domain

import java.time.Instant
import java.util.UUID

class VoteParticipant(
    val id: UUID = UUID.randomUUID(),
    val voteId: UUID,
    val email: String,
    var displayName: String? = null,
    val addedAt: Instant = Instant.now(),
)
