package com.juncevich.fate.vote.internal.domain

import com.juncevich.fate.shared.uuidV7
import java.time.Instant
import java.util.UUID

class VoteParticipant(
    val id: UUID = uuidV7(),
    val voteId: UUID,
    val email: String,
    var displayName: String? = null,
    val addedAt: Instant = Instant.now(),
)
