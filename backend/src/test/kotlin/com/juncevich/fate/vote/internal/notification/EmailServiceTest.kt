package com.juncevich.fate.vote.internal.notification

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mail.javamail.JavaMailSender
import java.util.Properties

class EmailServiceTest {
    private fun sentFrom(
        fromOverride: String,
        smtpUsername: String,
    ): String {
        val mailSender = mockk<JavaMailSender>()
        val sent = slot<MimeMessage>()
        every { mailSender.createMimeMessage() } answers { MimeMessage(Session.getInstance(Properties())) }
        every { mailSender.send(capture(sent)) } returns Unit

        EmailService(mailSender, fromOverride, smtpUsername)
            .sendVoteInvitation("p@test.com", "Vote", "Creator", "http://localhost/votes/1")

        verify(exactly = 1) { mailSender.send(any<MimeMessage>()) }
        return sent.captured.from
            .single()
            .toString()
    }

    @Test
    fun `falls back to the default sender when neither MAIL_FROM nor an SMTP username is set`() {
        // MAIL_USERNAME unset resolves to "" (application.yml: `${MAIL_USERNAME:}`), not to "absent"
        assertEquals("noreply@handoffate.app", sentFrom(fromOverride = "", smtpUsername = ""))
    }

    @Test
    fun `uses the SMTP username when no explicit sender is configured`() {
        assertEquals("mailer@example.com", sentFrom(fromOverride = "", smtpUsername = "mailer@example.com"))
    }

    @Test
    fun `an explicit sender wins over the SMTP username`() {
        assertEquals("votes@example.com", sentFrom(fromOverride = "votes@example.com", smtpUsername = "mailer@example.com"))
    }
}
