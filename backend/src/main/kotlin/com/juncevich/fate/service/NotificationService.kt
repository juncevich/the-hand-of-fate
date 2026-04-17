package com.juncevich.fate.service

import com.juncevich.fate.domain.vote.Vote
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val emailService: EmailService,
    @param:Value("\${app.frontend-url}") private val frontendUrl: String,
) {

    private val log = LoggerFactory.getLogger(NotificationService::class.java)

    @Async
    fun notifyVoteInvitation(recipientEmail: String, vote: Vote) {
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
    fun notifyDrawResult(vote: Vote, result: DrawResult, participantEmails: List<String>) {
        participantEmails.forEach { email ->
            runCatching {
                emailService.sendDrawResult(
                    to = email,
                    voteTitle = vote.title,
                    winnerName = result.winnerDisplayName ?: result.winnerEmail,
                    winnerEmail = result.winnerEmail,
                    round = result.round,
                    voteUrl = "$frontendUrl/votes/${vote.id}",
                )
            }.onFailure { log.error("Failed to send draw result email to $email", it) }
        }
    }
}
