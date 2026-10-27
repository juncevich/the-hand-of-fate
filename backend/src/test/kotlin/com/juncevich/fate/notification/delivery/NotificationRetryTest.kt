package com.juncevich.fate.notification.delivery

import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.EmailPreferences
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.NOTIFICATION_PAYLOAD_VERSION
import com.juncevich.fate.notification.port.EmailSuppressionPort
import com.juncevich.fate.shared.FrontendProperties
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.mail.autoconfigure.MailProperties
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Retry and concurrency limit of outgoing vote emails, exercised through real Spring proxies
 * (only the SMTP server is mocked).
 */
@SpringJUnitConfig(NotificationRetryTest.Config::class)
@TestPropertySource(
    properties = [
        "app.frontend-url=http://localhost:3000",
        "spring.mail.username=noreply@test.com",
        "app.mail.retry.delay-ms=10",
        "app.mail.max-concurrent-sends=2",
        "app.notifications.unsubscribe-secret=test-unsubscribe-secret"
    ]
)
class NotificationRetryTest
    @Autowired
    constructor(
        private val mailSender: JavaMailSender,
        private val adapter: NotificationAdapter,
    ) {
        @Configuration
        @EnableResilientMethods
        @Import(EmailService::class, SmtpTransport::class, MailTemplates::class, NotificationAdapter::class, EmailPreferences::class)
        @EnableConfigurationProperties(NotificationMailProperties::class, FrontendProperties::class, MailProperties::class)
        class Config {
            @Bean
            fun mailSender(): JavaMailSender = mockk(relaxed = true)

            @Bean
            fun suppressions(): EmailSuppressionPort = mockk(relaxed = true)
        }

        private val invitation = InvitationPayload(UUID.randomUUID(), "Vote", "Creator", "p@test.com")

        @BeforeEach
        fun setUp() {
            clearMocks(mailSender)
            every { mailSender.createMimeMessage() } answers { MimeMessage(Session.getInstance(Properties())) }
        }

        @Test
        fun `transient SMTP failures are retried until the email goes out`() {
            every { mailSender.send(any<MimeMessage>()) } throws
                MailSendException("SMTP down") andThenThrows
                MailSendException("SMTP still down") andThen Unit

            adapter.send(invitation)

            verify(exactly = 3) { mailSender.send(any<MimeMessage>()) }
        }

        @Test
        fun `gives up after three attempts and propagates the failure to the worker`() {
            every { mailSender.send(any<MimeMessage>()) } throws MailSendException("SMTP down")

            org.junit.jupiter.api
                .assertThrows<MailSendException> { adapter.send(invitation) }

            verify(exactly = 3) { mailSender.send(any<MimeMessage>()) }
        }

        @Test
        fun `caps simultaneous SMTP sends at the configured limit`() {
            val inFlight = AtomicInteger()
            val maxInFlight = AtomicInteger()
            val release = CountDownLatch(1)
            val started = CountDownLatch(2)
            every { mailSender.send(any<MimeMessage>()) } answers {
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                inFlight.decrementAndGet()
            }

            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                repeat(6) { i -> executor.execute { adapter.send(invitation.copy(recipientEmail = "p$i@test.com")) } }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                // Give the remaining senders a chance to (wrongly) get past the cap before releasing
                Thread.sleep(100)
                release.countDown()
            }

            assertEquals(2, maxInFlight.get())
            verify(exactly = 6) { mailSender.send(any<MimeMessage>()) }
        }
    }
