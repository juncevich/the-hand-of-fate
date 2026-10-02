package com.juncevich.fate.vote.internal.notification

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * SMTP delivery of a single persisted notification. Failures propagate: [NotificationWorker]
 * owns retries, logging and failure metrics; [EmailService] only retries transient SMTP errors.
 */
@Component
class NotificationAdapter(
    private val emailService: EmailService,
    @param:Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    fun send(payload: InvitationPayload) {
        emailService.sendVoteInvitation(
            to = payload.recipientEmail,
            voteTitle = payload.voteTitle,
            creatorName = payload.creatorName,
            voteUrl = voteUrl(payload.voteId)
        )
    }

    fun send(payload: DrawResultPayload) {
        emailService.sendDrawResult(
            to = payload.recipientEmail,
            voteTitle = payload.voteTitle,
            winnerName = payload.winnerName,
            winnerEmail = payload.winnerEmail.orEmpty(),
            round = payload.round,
            voteUrl = voteUrl(payload.voteId)
        )
    }

    private fun voteUrl(voteId: UUID) = "$frontendUrl/votes/$voteId"
}
