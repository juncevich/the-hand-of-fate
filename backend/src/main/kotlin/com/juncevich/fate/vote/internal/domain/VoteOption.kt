package com.juncevich.fate.vote.internal.domain

import com.juncevich.fate.shared.uuidV7
import java.time.Instant
import java.util.UUID

class VoteOption(
    val id: UUID = uuidV7(),
    val voteId: UUID,
    val title: String,
    val position: Int = 0,
    val createdAt: Instant = Instant.now(),
)
