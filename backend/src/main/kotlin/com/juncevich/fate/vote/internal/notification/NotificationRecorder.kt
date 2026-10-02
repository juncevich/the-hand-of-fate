package com.juncevich.fate.vote.internal.notification

import com.juncevich.fate.vote.internal.ParticipantInvited
import com.juncevich.fate.vote.internal.VoteDrawn
import com.juncevich.fate.vote.internal.port.NotificationOutboxPort
import com.juncevich.fate.vote.internal.port.NotificationType
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/** Runs synchronously inside the vote transaction, so the notification and vote commit together. */
@Component
class NotificationRecorder(
    private val outbox: NotificationOutboxPort,
    private val json: JsonMapper,
) {
    @EventListener
    fun on(event: ParticipantInvited) {
        val payload =
            InvitationPayload(
                voteId = event.voteId,
                voteTitle = event.voteTitle,
                creatorName = event.creatorName,
                recipientEmail = event.recipientEmail
            )
        outbox.append(NotificationType.INVITATION, json.writeValueAsString(payload))
    }

    @EventListener
    fun on(event: VoteDrawn) {
        val result = event.result
        val winnerName = (result.winnerOptionTitle ?: result.winnerDisplayName ?: result.winnerEmail).orEmpty()
        event.participantEmails.distinct().forEach { email ->
            val payload =
                DrawResultPayload(
                    voteId = event.voteId,
                    voteTitle = event.voteTitle,
                    recipientEmail = email,
                    winnerName = winnerName,
                    winnerEmail = result.winnerEmail,
                    round = result.round
                )
            outbox.append(NotificationType.DRAW_RESULT, json.writeValueAsString(payload))
        }
    }
}
