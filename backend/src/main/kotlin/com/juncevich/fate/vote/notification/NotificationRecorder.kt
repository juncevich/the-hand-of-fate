package com.juncevich.fate.vote.notification

import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.NotificationOutbox
import com.juncevich.fate.vote.Winner
import com.juncevich.fate.vote.domain.ParticipantInvited
import com.juncevich.fate.vote.domain.VoteDrawn
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Runs synchronously inside the vote transaction, so the notification and vote commit together. */
@Component
class NotificationRecorder(
    private val outbox: NotificationOutbox,
) {
    @EventListener
    fun on(event: ParticipantInvited) {
        outbox.record(
            InvitationPayload(
                voteId = event.voteId.value,
                voteTitle = event.voteTitle,
                creatorName = event.creatorName,
                recipientEmail = event.recipientEmail.value
            )
        )
    }

    @EventListener
    fun on(event: VoteDrawn) {
        val result = event.result
        val winnerName = result.winner.label
        val winnerEmail = (result.winner as? Winner.Participant)?.email
        event.participantEmails.distinct().forEach { email ->
            outbox.record(
                DrawResultPayload(
                    voteId = event.voteId.value,
                    voteTitle = event.voteTitle,
                    recipientEmail = email.value,
                    winnerName = winnerName,
                    winnerEmail = winnerEmail,
                    round = result.round
                )
            )
        }
    }
}
