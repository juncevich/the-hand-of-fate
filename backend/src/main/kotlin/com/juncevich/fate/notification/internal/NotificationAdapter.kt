package com.juncevich.fate.notification.internal

import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.EmailVerificationPayload
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.PasswordResetPayload
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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

    fun send(payload: EmailVerificationPayload) {
        emailService.sendEmailVerification(
            to = payload.recipientEmail,
            displayName = payload.displayName,
            link = "$frontendUrl/verify-email?token=${encode(payload.token)}"
        )
    }

    fun send(payload: PasswordResetPayload) {
        emailService.sendPasswordReset(
            to = payload.recipientEmail,
            displayName = payload.displayName,
            link = "$frontendUrl/reset-password?token=${encode(payload.token)}"
        )
    }

    private fun voteUrl(voteId: UUID) = "$frontendUrl/votes/$voteId"

    private fun encode(token: String) = URLEncoder.encode(token, StandardCharsets.UTF_8)
}
