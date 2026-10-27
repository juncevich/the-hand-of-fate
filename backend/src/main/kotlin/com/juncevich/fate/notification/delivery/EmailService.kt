package com.juncevich.fate.notification.delivery

import org.springframework.boot.mail.autoconfigure.MailProperties
import org.springframework.mail.MailException
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service

/** Where a notification email's recipient can opt out: the confirmation page and the RFC 8058 endpoint. */
data class UnsubscribeLinks(
    val page: String,
    val oneClick: String,
)

/**
 * Sends the notification emails rendered by [MailTemplates].
 *
 * Transient SMTP failures ([MailException]) are retried: 3 attempts in total, backing off
 * 1s then 2s by default (`app.mail.retry.delay-ms` sets the initial delay). Each attempt goes
 * through [SmtpTransport], which caps simultaneous sends.
 */
@Service
@Retryable(
    includes = [MailException::class],
    maxRetries = 2,
    delayString = "\${app.mail.retry.delay-ms:1000}",
    multiplier = 2.0
)
class EmailService(
    private val transport: SmtpTransport,
    private val templates: MailTemplates,
    properties: NotificationMailProperties,
    smtp: MailProperties,
) {
    private val from: String = properties.sender(smtp.username)

    fun sendVoteInvitation(
        to: String,
        voteTitle: String,
        creatorName: String,
        voteUrl: String,
        unsubscribe: UnsubscribeLinks,
    ) = send(to, templates.invitation(voteTitle, creatorName, voteUrl, unsubscribe.page), unsubscribe)

    fun sendDrawResult(
        to: String,
        voteTitle: String,
        winnerName: String,
        winnerEmail: String,
        round: Int,
        voteUrl: String,
        unsubscribe: UnsubscribeLinks,
    ) = send(
        to,
        templates.drawResult(voteTitle, winnerName, winnerEmail, round, voteUrl, unsubscribe.page),
        unsubscribe
    )

    private fun send(
        to: String,
        mail: RenderedMail,
        unsubscribe: UnsubscribeLinks,
    ) {
        val message = transport.createMessage()
        MimeMessageHelper(message, true, "UTF-8").apply {
            setFrom(from)
            setTo(to)
            setSubject(mail.subject)
            setText(mail.html, true)
        }
        // RFC 8058 one-click unsubscribe: mail clients POST to the URL without opening a page.
        message.setHeader("List-Unsubscribe", "<${unsubscribe.oneClick}>")
        message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")
        transport.send(message)
    }
}
