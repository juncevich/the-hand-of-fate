package com.juncevich.fate.vote.internal.notification

import com.juncevich.fate.vote.DrawResult
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.port.NotificationPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

private const val MAX_SEND_ATTEMPTS = 3

@Component
class NotificationAdapter(
    private val emailService: EmailService,
    private val meterRegistry: MeterRegistry,
    @param:Value("\${app.frontend-url}") private val frontendUrl: String,
) : NotificationPort {
    private val log = LoggerFactory.getLogger(NotificationAdapter::class.java)

    @Async
    override fun notifyVoteInvitation(
        recipientEmail: String,
        vote: Vote,
    ) {
        withRetry("invitation email to $recipientEmail", type = "invitation") {
            emailService.sendVoteInvitation(
                to = recipientEmail,
                voteTitle = vote.title,
                creatorName = vote.creator.displayName,
                voteUrl = "$frontendUrl/votes/${vote.id}"
            )
        }
    }

    @Async
    override fun notifyDrawResult(
        vote: Vote,
        result: DrawResult,
        participantEmails: List<String>,
    ) {
        participantEmails.forEach { email ->
            withRetry("draw result email to $email", type = "draw-result") {
                emailService.sendDrawResult(
                    to = email,
                    voteTitle = vote.title,
                    winnerName = result.winnerOptionTitle ?: result.winnerDisplayName ?: result.winnerEmail ?: "",
                    winnerEmail = result.winnerEmail ?: "",
                    round = result.round,
                    voteUrl = "$frontendUrl/votes/${vote.id}"
                )
            }
        }
    }

    private fun withRetry(
        description: String,
        type: String,
        action: () -> Unit,
    ) {
        var lastError: Throwable? = null
        repeat(MAX_SEND_ATTEMPTS) { attempt ->
            runCatching(action).onSuccess { return }.onFailure { lastError = it }
            if (attempt < MAX_SEND_ATTEMPTS - 1) Thread.sleep(1000L * (attempt + 1))
        }
        log.error("Failed to send $description after $MAX_SEND_ATTEMPTS attempts", lastError)
        meterRegistry.counter("notification.failed", "type", type).increment()
    }
}
