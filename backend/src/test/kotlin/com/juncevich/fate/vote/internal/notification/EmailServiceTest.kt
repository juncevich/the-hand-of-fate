package com.juncevich.fate.vote.internal.notification

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mail.javamail.JavaMailSender
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun `caps simultaneous SMTP sends at the configured limit`() {
        val mailSender = mockk<JavaMailSender>()
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val release = CountDownLatch(1)
        val started = CountDownLatch(2)
        every { mailSender.createMimeMessage() } answers { MimeMessage(Session.getInstance(Properties())) }
        every { mailSender.send(any<MimeMessage>()) } answers {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            inFlight.decrementAndGet()
        }
        val service = EmailService(mailSender, "", "", maxConcurrentSends = 2)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            repeat(6) { i ->
                executor.execute { service.sendVoteInvitation("p$i@test.com", "Vote", "Creator", "http://localhost/votes/1") }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            // Give the remaining senders a chance to (wrongly) get past the cap before releasing
            Thread.sleep(100)
            release.countDown()
        }

        assertEquals(2, maxInFlight.get())
        verify(exactly = 6) { mailSender.send(any<MimeMessage>()) }
    }
}
