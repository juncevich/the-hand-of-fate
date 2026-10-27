package com.juncevich.fate.vote.notification

import com.juncevich.fate.account.UserQueryService
import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.EmailBudget
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.NotificationOutbox
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.vote.VoteId
import com.juncevich.fate.vote.Winner
import com.juncevich.fate.vote.domain.ParticipantInvited
import com.juncevich.fate.vote.domain.VoteCreated
import com.juncevich.fate.vote.domain.VoteDrawn
import com.juncevich.fate.vote.domain.winner
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Turns vote events into outbox jobs, one per recipient, charging the email budget of whoever
 * caused them. Runs synchronously inside the vote transaction, so the notifications and the vote
 * commit together — and an invitation over the budget rolls the whole change back (429), while a
 * draw over the budget still happens, only without its result emails.
 */
@Component
class NotificationRecorder(
    private val outbox: NotificationOutbox,
    private val emailBudget: EmailBudget,
    private val users: UserQueryService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun on(event: VoteCreated) {
        if (event.invited.isEmpty()) return
        emailBudget.reserveOrThrow(event.creatorId, event.invited.size)
        val creatorName = nameOf(event.creatorId)
        event.invited.forEach { invite(event.voteId, event.voteTitle, creatorName, it) }
    }

    @EventListener
    fun on(event: ParticipantInvited) {
        emailBudget.reserveOrThrow(event.invitedBy, 1)
        // Only the creator invites, so the inviter's name is the creator's.
        invite(event.voteId, event.voteTitle, nameOf(event.invitedBy), event.recipientEmail)
    }

    @EventListener
    fun on(event: VoteDrawn) {
        val recipients = event.recipients.distinct()
        if (!emailBudget.tryReserve(event.drawnBy, recipients.size)) {
            log.warn("Result emails for draw of vote {} suppressed: email budget exhausted", event.voteId)
            return
        }
        val entry = event.outcome.entry
        val winner = entry.winner
        recipients.forEach { email ->
            outbox.record(
                DrawResultPayload(
                    voteId = event.voteId.value,
                    voteTitle = event.voteTitle,
                    recipientEmail = email.value,
                    winnerName = winner.label,
                    winnerEmail = (winner as? Winner.Participant)?.email,
                    round = entry.round
                )
            )
        }
    }

    private fun invite(
        voteId: VoteId,
        voteTitle: String,
        creatorName: String,
        recipient: Email,
    ) = outbox.record(
        InvitationPayload(
            voteId = voteId.value,
            voteTitle = voteTitle,
            creatorName = creatorName,
            recipientEmail = recipient.value
        )
    )

    private fun nameOf(userId: UserId): String =
        checkNotNull(users.findProfileById(userId)) { "Inviting user $userId not found" }.displayName
}
