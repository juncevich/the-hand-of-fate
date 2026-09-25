package com.juncevich.fate.vote.internal.notification

import com.juncevich.fate.vote.internal.ParticipantInvited
import com.juncevich.fate.vote.internal.VoteDrawn
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Sends vote emails in reaction to [ParticipantInvited] / [VoteDrawn]. `@TransactionalEventListener`
 * (AFTER_COMMIT by default) fires only once the publishing transaction has committed, and `@Async`
 * moves the SMTP round-trips off the request thread. No transaction is opened here on purpose, so a
 * slow mail server never holds a pooled DB connection. Retries live on [EmailService] (`@Retryable`);
 * what still fails afterwards is logged and counted, never propagated.
 */
@Component
class NotificationAdapter(
    private val emailService: EmailService,
    private val meterRegistry: MeterRegistry,
    @param:Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    private val log = LoggerFactory.getLogger(NotificationAdapter::class.java)

    @Async
    @TransactionalEventListener
    fun on(event: ParticipantInvited) {
        deliver("invitation email to ${event.recipientEmail}", type = "invitation") {
            emailService.sendVoteInvitation(
                to = event.recipientEmail,
                voteTitle = event.voteTitle,
                creatorName = event.creatorName,
                voteUrl = "$frontendUrl/votes/${event.voteId}"
            )
        }
    }

    @Async
    @TransactionalEventListener
    fun on(event: VoteDrawn) {
        val result = event.result
        event.participantEmails.forEach { email ->
            deliver("draw result email to $email", type = "draw-result") {
                emailService.sendDrawResult(
                    to = email,
                    voteTitle = event.voteTitle,
                    winnerName = (result.winnerOptionTitle ?: result.winnerDisplayName ?: result.winnerEmail).orEmpty(),
                    winnerEmail = result.winnerEmail.orEmpty(),
                    round = result.round,
                    voteUrl = "$frontendUrl/votes/${event.voteId}"
                )
            }
        }
    }

    private fun deliver(
        description: String,
        type: String,
        send: () -> Unit,
    ) {
        runCatching(send).onFailure {
            log.error("Failed to send $description", it)
            meterRegistry.counter("notification.failed", "type", type).increment()
        }
    }
}
