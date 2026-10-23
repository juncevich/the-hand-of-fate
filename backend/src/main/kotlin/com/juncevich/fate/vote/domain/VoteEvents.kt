package com.juncevich.fate.vote.domain

import com.juncevich.fate.shared.Email
import com.juncevich.fate.vote.DrawResult
import com.juncevich.fate.vote.VoteId

/**
 * Domain events published by `VoteService`. The recorder persists recipient jobs in the same
 * transaction; the delivery worker sees them only after commit.
 */
data class ParticipantInvited(
    val voteId: VoteId,
    val voteTitle: String,
    val creatorName: String,
    val recipientEmail: Email,
)

data class VoteDrawn(
    val voteId: VoteId,
    val voteTitle: String,
    val result: DrawResult,
    val participantEmails: List<Email>,
)
