package com.juncevich.fate.vote.domain

import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.vote.VoteId

/**
 * What happened to a [Vote], recorded by the aggregate itself and published by `VoteService`
 * after the vote is saved ([Vote.pullEvents]). Listeners run synchronously inside the vote's
 * transaction: `NotificationRecorder` turns them into outbox jobs (and charges the email budget),
 * so the notifications commit — or roll back — with the change.
 */
sealed interface VoteEvent {
    val voteId: VoteId
    val voteTitle: String
}

/** A new vote; [invited] are its participants other than the creator. */
data class VoteCreated(
    override val voteId: VoteId,
    override val voteTitle: String,
    val creatorId: UserId,
    val invited: List<Email>,
) : VoteEvent

/** [recipientEmail] was added to an existing vote by [invitedBy] (always its creator). */
data class ParticipantInvited(
    override val voteId: VoteId,
    override val voteTitle: String,
    val invitedBy: UserId,
    val recipientEmail: Email,
) : VoteEvent

/** A draw by [drawnBy]; [recipients] are the vote's participants, who are told the result. */
data class VoteDrawn(
    override val voteId: VoteId,
    override val voteTitle: String,
    val drawnBy: UserId,
    val outcome: DrawOutcome,
    val recipients: List<Email>,
) : VoteEvent
