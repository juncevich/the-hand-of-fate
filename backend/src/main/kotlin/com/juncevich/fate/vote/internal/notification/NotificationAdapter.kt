package com.juncevich.fate.vote.internal.notification

import com.juncevich.fate.vote.DrawResult
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.port.NotificationPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class NotificationAdapter(
    private val emailService: EmailService,
    @param:Value("\${app.frontend-url}") private val frontendUrl: String,
) : NotificationPort {
    private val log = LoggerFactory.getLogger(NotificationAdapter::class.java)

    @Async
    override fun notifyVoteInvitation(recipientEmail: String, vote: Vote) {
        runCatching {
            emailService.sendVoteInvitation(
                to = recipientEmail,
                voteTitle = vote.title,
                creatorName = vote.creator.displayName,
                voteUrl = "$frontendUrl/votes/${vote.id}",
            )
        }.onFailure { log.error("Failed to send invitation email to $recipientEmail", it) }
    }

    @Async
    override fun notifyDrawResult(vote: Vote, result: DrawResult, participantEmails: List<String>) {
        participantEmails.forEach { email ->
            runCatching {
                emailService.sendDrawResult(
                    to = email,
                    voteTitle = vote.title,
                    winnerName = result.winnerOptionTitle ?: result.winnerDisplayName ?: result.winnerEmail ?: "",
                    winnerEmail = result.winnerEmail ?: "",
                    round = result.round,
                    voteUrl = "$frontendUrl/votes/${vote.id}",
                )
            }.onFailure { log.error("Failed to send draw result email to $email", it) }
        }
    }
}
