package com.juncevich.fate.notification.delivery

import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.EmailPreferences
import com.juncevich.fate.notification.InvitationPayload
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
    private val preferences: EmailPreferences,
    @param:Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    fun send(payload: InvitationPayload) {
        emailService.sendVoteInvitation(
            to = payload.recipientEmail,
            voteTitle = payload.voteTitle,
            creatorName = payload.creatorName,
            voteUrl = voteUrl(payload.voteId),
            unsubscribe = unsubscribeLinks(payload.recipientEmail)
        )
    }

    fun send(payload: DrawResultPayload) {
        emailService.sendDrawResult(
            to = payload.recipientEmail,
            voteTitle = payload.voteTitle,
            winnerName = payload.winnerName,
            winnerEmail = payload.winnerEmail.orEmpty(),
            round = payload.round,
            voteUrl = voteUrl(payload.voteId),
            unsubscribe = unsubscribeLinks(payload.recipientEmail)
        )
    }

    private fun voteUrl(voteId: UUID) = "$frontendUrl/votes/$voteId"

    // The API is served from the same origin as the SPA (nginx proxies /api/).
    private fun unsubscribeLinks(email: String): UnsubscribeLinks {
        val query = "email=${encode(email)}&sig=${preferences.sign(email)}"
        return UnsubscribeLinks(
            page = "$frontendUrl/unsubscribe?$query",
            oneClick = "$frontendUrl/api/v1/notifications/unsubscribe/one-click?$query"
        )
    }

    private fun encode(token: String) = URLEncoder.encode(token, StandardCharsets.UTF_8)
}
