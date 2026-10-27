package com.juncevich.fate.notification.delivery

import com.juncevich.fate.notification.EmailPreferences
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.port.EmailSuppressionPort
import com.juncevich.fate.shared.FrontendProperties
import io.mockk.every
import io.mockk.mockk
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The send permit covers one attempt, not a retry backoff: `@ConcurrencyLimit` sits on [SmtpTransport],
 * apart from `EmailService`'s `@Retryable`. Real Spring proxies; a long backoff makes the difference visible.
 */
@SpringJUnitConfig(SmtpConcurrencyLimitTest.Config::class)
@TestPropertySource(
    properties = [
        "app.frontend-url=http://localhost:3000",
        "spring.mail.username=noreply@test.com",
        "app.mail.retry.delay-ms=1000",
        "app.mail.max-concurrent-sends=2",
        "app.notifications.unsubscribe-secret=test-unsubscribe-secret"
    ]
)
class SmtpConcurrencyLimitTest
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

        private fun invitation(to: String) = InvitationPayload(UUID.randomUUID(), "Vote", "Creator", to)

        @Test
        fun `a sender waiting out a retry backoff holds no permit`() {
            val slowFailed = CountDownLatch(1)
            // The two other sends go out only if both are in flight together, i.e. both permits are free.
            val together = CountDownLatch(2)
            val metWhileSlowBacksOff = ConcurrentLinkedQueue<Boolean>()
            every { mailSender.createMimeMessage() } answers { MimeMessage(Session.getInstance(Properties())) }
            every { mailSender.send(any<MimeMessage>()) } answers {
                val to = firstArg<MimeMessage>().allRecipients.single().toString()
                when {
                    to != "slow@test.com" -> {
                        together.countDown()
                        metWhileSlowBacksOff += together.await(500, TimeUnit.MILLISECONDS)
                    }

                    slowFailed.count > 0 -> {
                        slowFailed.countDown()
                        throw MailSendException("SMTP down")
                    }
                }
            }

            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                executor.execute { adapter.send(invitation("slow@test.com")) }
                slowFailed.await(5, TimeUnit.SECONDS)
                repeat(2) { i -> executor.execute { adapter.send(invitation("fast$i@test.com")) } }
            }

            assertEquals(listOf(true, true), metWhileSlowBacksOff.toList())
        }
    }
