package com.juncevich.fate.vote.internal.notification

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
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
class NotificationRetryTest
    @Autowired
    constructor(
        private val mailSender: JavaMailSender,
        private val adapter: NotificationAdapter,
    ) {
        @Configuration
        @EnableResilientMethods
        @Import(EmailService::class, NotificationAdapter::class)
        class Config {
            @Bean
            fun mailSender(): JavaMailSender = mockk(relaxed = true)
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
    }
