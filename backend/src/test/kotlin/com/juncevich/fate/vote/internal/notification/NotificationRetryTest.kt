package com.juncevich.fate.vote.internal.notification

import com.juncevich.fate.vote.DrawResult
import com.juncevich.fate.vote.internal.ParticipantInvited
import com.juncevich.fate.vote.internal.VoteDrawn
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.util.Properties
import java.util.UUID

/**
 * Retry behaviour of outgoing vote emails, exercised through real Spring proxies
 * (only the SMTP transport is mocked).
 */
@SpringJUnitConfig(NotificationRetryTest.Config::class)
@TestPropertySource(
    properties = [
        "app.frontend-url=http://localhost:3000",
        "spring.mail.username=noreply@test.com",
        "app.mail.retry.delay-ms=10"
    ]
)
class NotificationRetryTest {
    @Configuration
    @EnableResilientMethods
    @Import(EmailService::class, NotificationAdapter::class)
    class Config {
        @Bean
        fun mailSender(): JavaMailSender = mockk(relaxed = true)

        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
    }

    @Autowired
    private lateinit var mailSender: JavaMailSender

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var adapter: NotificationAdapter

    private val invitation = ParticipantInvited(UUID.randomUUID(), "Vote", "Creator", "p@test.com")

    @BeforeEach
    fun setUp() {
        clearMocks(mailSender)
        meterRegistry.clear()
        every { mailSender.createMimeMessage() } answers { MimeMessage(Session.getInstance(Properties())) }
    }

    private fun failedCount(type: String) =
        meterRegistry
            .find("notification.failed")
            .tag("type", type)
            .counter()
            ?.count() ?: 0.0

    @Test
    fun `transient SMTP failures are retried until the email goes out`() {
        every { mailSender.send(any<MimeMessage>()) } throws
            MailSendException("SMTP down") andThenThrows
            MailSendException("SMTP still down") andThen Unit

        adapter.on(invitation)

        verify(exactly = 3) { mailSender.send(any<MimeMessage>()) }
        assertEquals(0.0, failedCount("invitation"))
    }

    @Test
    fun `gives up after three attempts and counts the failure`() {
        every { mailSender.send(any<MimeMessage>()) } throws MailSendException("SMTP down")

        adapter.on(invitation)

        verify(exactly = 3) { mailSender.send(any<MimeMessage>()) }
        assertEquals(1.0, failedCount("invitation"))
    }

    @Test
    fun `one recipient failing does not stop the others`() {
        every { mailSender.send(any<MimeMessage>()) } answers {
            val to = firstArg<MimeMessage>().allRecipients.single().toString()
            if (to == "bad@test.com") throw MailSendException("mailbox unavailable")
        }
        val result = DrawResult(null, null, "Option", 1, false)

        adapter.on(VoteDrawn(UUID.randomUUID(), "Vote", result, listOf("bad@test.com", "good@test.com")))

        verify(exactly = 4) { mailSender.send(any<MimeMessage>()) }
        assertEquals(1.0, failedCount("draw-result"))
    }
}
