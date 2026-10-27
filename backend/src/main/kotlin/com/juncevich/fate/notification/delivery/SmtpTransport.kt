package com.juncevich.fate.notification.delivery

import jakarta.mail.internet.MimeMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.resilience.annotation.ConcurrencyLimit
import org.springframework.stereotype.Component

/**
 * One SMTP send attempt. Callers run on virtual threads (bounded outbox batches, per-recipient
 * fan-out), so the number of simultaneous sends is capped here (`app.mail.max-concurrent-sends`,
 * validated by [NotificationMailProperties]) to keep the mail server from throttling or rejecting us.
 *
 * A bean of its own, apart from [EmailService]'s `@Retryable`: the permit then covers a single
 * attempt, never a retry backoff, whatever order Spring would give two interceptors on one method.
 */
@Component
@ConcurrencyLimit(limitString = "\${app.mail.max-concurrent-sends:4}")
class SmtpTransport(
    private val mailSender: JavaMailSender,
) {
    fun createMessage(): MimeMessage = mailSender.createMimeMessage()

    fun send(message: MimeMessage) = mailSender.send(message)
}
