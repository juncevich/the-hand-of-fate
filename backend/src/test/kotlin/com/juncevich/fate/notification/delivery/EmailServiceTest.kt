package com.juncevich.fate.notification.delivery

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
            .sendVoteInvitation("p@test.com", "Vote", "Creator", "http://localhost/votes/1", UnsubscribeLinks("http://localhost/unsubscribe", "http://localhost/one-click"))

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
                executor.execute { service.sendVoteInvitation("p$i@test.com", "Vote", "Creator", "http://localhost/votes/1", UnsubscribeLinks("http://localhost/unsubscribe", "http://localhost/one-click")) }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            // Give the remaining senders a chance to (wrongly) get past the cap before releasing
            Thread.sleep(100)
            release.countDown()
        }

        assertEquals(2, maxInFlight.get())
        verify(exactly = 6) { mailSender.send(any<MimeMessage>()) }
    }

    private fun sentHtml(send: EmailService.() -> Unit): String {
        val mailSender = mockk<JavaMailSender>()
        val sent = slot<MimeMessage>()
        every { mailSender.createMimeMessage() } answers { MimeMessage(Session.getInstance(Properties())) }
        every { mailSender.send(capture(sent)) } returns Unit
        EmailService(mailSender, "", "").send()
        val multipart = sent.captured.content as jakarta.mail.Multipart
        return generateSequence(multipart.getBodyPart(0)) { part ->
            (part.content as? jakarta.mail.Multipart)?.getBodyPart(0)
        }.last().content.toString()
    }

    @Test
    fun `user-supplied text is escaped in the email html`() {
        val html =
            sentHtml {
                sendVoteInvitation("p@test.com", "<a href=\"https://evil\">Win</a>", "<b>Eve</b>", "http://localhost/votes/1", UnsubscribeLinks("http://localhost/unsubscribe", "http://localhost/one-click"))
            }

        assertTrue("&lt;a href=&quot;https://evil&quot;&gt;Win&lt;/a&gt;" in html, html)
        assertTrue("&lt;b&gt;Eve&lt;/b&gt;" in html)
        assertTrue("<a href=\"https://evil\"" !in html)
    }

    private fun sentMessage(send: EmailService.() -> Unit): MimeMessage {
        val mailSender = mockk<JavaMailSender>()
        val sent = slot<MimeMessage>()
        every { mailSender.createMimeMessage() } answers { MimeMessage(Session.getInstance(Properties())) }
        every { mailSender.send(capture(sent)) } returns Unit
        EmailService(mailSender, "", "").send()
        return sent.captured
    }

    @Test
    fun `notification emails offer one-click unsubscribe and an unsubscribe link`() {
        val links = UnsubscribeLinks("http://app/unsubscribe?email=a&sig=s", "http://app/api/one-click?email=a&sig=s")
        val message = sentMessage { sendVoteInvitation("a@test.com", "Vote", "Eve", "http://app/votes/1", links) }

        assertEquals("<http://app/api/one-click?email=a&sig=s>", message.getHeader("List-Unsubscribe").single())
        assertEquals("List-Unsubscribe=One-Click", message.getHeader("List-Unsubscribe-Post").single())
        val html = sentHtml { sendVoteInvitation("a@test.com", "Vote", "Eve", "http://app/votes/1", links) }
        assertTrue("http://app/unsubscribe?email=a&sig=s" in html, html)
    }
}
