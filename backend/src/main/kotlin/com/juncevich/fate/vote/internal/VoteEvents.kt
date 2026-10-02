package com.juncevich.fate.vote.internal

import com.juncevich.fate.vote.DrawResult
import java.util.UUID

/**
 * Domain events published by `VoteService`. The recorder persists recipient jobs in the same
 * transaction; the delivery worker sees them only after commit.
 */
data class ParticipantInvited(
    val voteId: UUID,
    val voteTitle: String,
    val creatorName: String,
    val recipientEmail: String,
)

data class VoteDrawn(
    val voteId: UUID,
    val voteTitle: String,
    val result: DrawResult,
    val participantEmails: List<String>,
)
