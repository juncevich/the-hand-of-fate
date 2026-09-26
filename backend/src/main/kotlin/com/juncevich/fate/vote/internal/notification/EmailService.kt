package com.juncevich.fate.vote.internal.notification

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service
import java.util.concurrent.Semaphore

/**
 * Transient SMTP failures ([MailException]) are retried: 3 attempts in total, backing off
 * 1s then 2s by default (`app.mail.retry.delay-ms` sets the initial delay).
 *
 * Callers run on unbounded virtual threads (`@Async`, per-recipient fan-out in [NotificationAdapter]),
 * so the number of simultaneous SMTP sends is capped here (`app.mail.max-concurrent-sends`) to keep
 * the mail server from throttling or rejecting us. The permit covers a single attempt only, so a
 * sender waiting out a retry backoff doesn't block others.
 */
@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from:}") fromOverride: String,
    @Value("\${spring.mail.username:}") smtpUsername: String,
    @Value("\${app.mail.max-concurrent-sends:4}") maxConcurrentSends: Int =
        DEFAULT_MAX_CONCURRENT_SENDS,
) {
    private val sendPermits = Semaphore(maxConcurrentSends)

    // An unset MAIL_USERNAME resolves to "" rather than "absent", so a placeholder default
    // never kicks in; pick the first non-blank candidate explicitly.
    private val from: String =
        listOf(fromOverride, smtpUsername).firstOrNull { it.isNotBlank() } ?: DEFAULT_FROM

    @Retryable(
        includes = [MailException::class],
        maxRetries = 2,
        delayString = "\${app.mail.retry.delay-ms:1000}",
        multiplier = 2.0
    )
    fun sendVoteInvitation(
        to: String,
        voteTitle: String,
        creatorName: String,
        voteUrl: String,
    ) {
        send(
            to = to,
            subject = "✦ You've been invited to a vote: $voteTitle",
            html = invitationHtml(voteTitle, creatorName, voteUrl)
        )
    }

    @Retryable(
        includes = [MailException::class],
        maxRetries = 2,
        delayString = "\${app.mail.retry.delay-ms:1000}",
        multiplier = 2.0
    )
    fun sendDrawResult(
        to: String,
        voteTitle: String,
        winnerName: String,
        winnerEmail: String,
        round: Int,
        voteUrl: String,
    ) {
        send(
            to = to,
            subject = "✦ Vote result: $voteTitle — Winner: $winnerName",
            html = drawResultHtml(voteTitle, winnerName, winnerEmail, round, voteUrl)
        )
    }

    private fun send(
        to: String,
        subject: String,
        html: String,
    ) {
        val message = mailSender.createMimeMessage()
        MimeMessageHelper(message, true, "UTF-8").apply {
            setFrom(from)
            setTo(to)
            setSubject(subject)
            setText(html, true)
        }
        sendPermits.acquire()
        try {
            mailSender.send(message)
        } finally {
            sendPermits.release()
        }
    }

    private fun invitationHtml(
        voteTitle: String,
        creatorName: String,
        voteUrl: String,
    ) = """
        <!DOCTYPE html>
        <html><body style="font-family:sans-serif;background:#0d0d1a;color:#f0f0f0;padding:24px">
        <h2 style="color:#f59e0b">✦ The Hand of Fate</h2>
        <p><strong>$creatorName</strong> has invited you to participate in:</p>
        <h3 style="color:#fff">$voteTitle</h3>
        <a href="$voteUrl" style="display:inline-block;background:#f59e0b;color:#000;padding:12px 24px;
            border-radius:8px;text-decoration:none;font-weight:bold;margin-top:16px">
          View Vote
        </a>
        </body></html>
        """.trimIndent()

    private fun drawResultHtml(
        voteTitle: String,
        winnerName: String,
        winnerEmail: String,
        round: Int,
        voteUrl: String,
    ) = """
        <!DOCTYPE html>
        <html><body style="font-family:sans-serif;background:#0d0d1a;color:#f0f0f0;padding:24px">
        <h2 style="color:#f59e0b">✦ The Hand of Fate has spoken!</h2>
        <h3 style="color:#fff">Vote: $voteTitle</h3>
        <p style="font-size:18px">The winner of round <strong>$round</strong> is:</p>
        <p style="font-size:24px;color:#f59e0b;font-weight:bold">$winnerName</p>
        <p style="color:#a0a0b0">$winnerEmail</p>
        <a href="$voteUrl" style="display:inline-block;background:#f59e0b;color:#000;padding:12px 24px;
            border-radius:8px;text-decoration:none;font-weight:bold;margin-top:16px">
          View Results
        </a>
        </body></html>
        """.trimIndent()

    private companion object {
        const val DEFAULT_FROM = "noreply@handoffate.app"
        const val DEFAULT_MAX_CONCURRENT_SENDS = 4
    }
}
