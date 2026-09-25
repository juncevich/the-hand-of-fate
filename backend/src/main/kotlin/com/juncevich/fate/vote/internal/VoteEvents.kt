package com.juncevich.fate.vote.internal

import com.juncevich.fate.vote.DrawResult
import java.util.UUID

/**
 * Domain events published by `VoteService`. Listeners run only after the publishing
 * transaction commits, so a rolled-back change never notifies anyone.
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
