package com.juncevich.fate.notification.persistence

import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.EmailPreferences
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.delivery.EmailService
import com.juncevich.fate.notification.delivery.NotificationAdapter
import com.juncevich.fate.notification.delivery.NotificationProperties
import com.juncevich.fate.notification.delivery.NotificationWorker
import com.juncevich.fate.notification.port.EmailSuppressionPort
import com.juncevich.fate.notification.port.NotificationOutboxPort
import com.juncevich.fate.notification.port.NotificationType
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.FrontendProperties
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.vote.VoteId
import com.juncevich.fate.vote.domain.DrawHistory
import com.juncevich.fate.vote.domain.DrawOutcome
import com.juncevich.fate.vote.domain.ParticipantInvited
import com.juncevich.fate.vote.domain.VoteDrawn
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = ["spring.grpc.server.enabled=false"])
@ActiveProfiles("test")
@Testcontainers
class NotificationOutboxIntegrationTest
    @Autowired
    constructor(
        private val outbox: NotificationOutboxPort,
        private val events: ApplicationEventPublisher,
        private val jdbc: JdbcTemplate,
        private val transactions: PlatformTransactionManager,
        private val json: JsonMapper,
        private val preferences: EmailPreferences,
        private val suppressions: EmailSuppressionPort,
    ) {
        companion object {
            @Container @JvmField
            val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17")

            @DynamicPropertySource @JvmStatic
            fun datasource(registry: DynamicPropertyRegistry) {
                registry.add("spring.datasource.url", postgres::getJdbcUrl)
                registry.add("spring.datasource.username", postgres::getUsername)
                registry.add("spring.datasource.password", postgres::getPassword)
            }
        }

        // The recorder charges the creator's email budget and invites by their name: a real account.
        private val creatorId = UserId(UUID.randomUUID())
        private val invitation = ParticipantInvited(VoteId(UUID.randomUUID()), "Vote", creatorId, Email.of("recipient@test.com"))
        private val lease = Duration.ofMinutes(2)
        private val retryDelay = Duration.ofMinutes(1)

        @BeforeEach
        fun clearQueue() {
            jdbc.update("DELETE FROM notification_outbox")
            jdbc.update(
                "INSERT INTO users (id, email, display_name, created_at, updated_at) VALUES (?, ?, 'Creator', now(), now())",
                creatorId.value,
                "creator-$creatorId@test.com"
            )
        }

        private fun publish(event: Any) = TransactionTemplate(transactions).executeWithoutResult { events.publishEvent(event) }

        private fun drawn(
            round: Int,
            recipients: List<String>,
        ) = VoteDrawn(
            VoteId(UUID.randomUUID()),
            "Vote",
            creatorId,
            DrawOutcome(
                DrawHistory.OptionWinner(voteId = VoteId(UUID.randomUUID()), optionId = null, optionTitle = "Winner", round = round, drawnAt = Instant.now()),
                newRoundStarted = false
            ),
            recipients.map(Email::of)
        )

        @Test
        fun `notification rolls back with the vote transaction`() {
            TransactionTemplate(transactions).executeWithoutResult { status ->
                events.publishEvent(invitation)
                status.setRollbackOnly()
            }
            assertNull(claim())
        }

        @Test
        fun `committed invitation is persisted and can be consumed after recorder returns`() {
            publish(invitation)
            val job = checkNotNull(claim())
            assertEquals(NotificationType.INVITATION, job.type)
            assertEquals(1, job.attempts)
            assertEquals(
                InvitationPayload(invitation.voteId.value, "Vote", "Creator", "recipient@test.com"),
                json.readValue(job.payload, InvitationPayload::class.java)
            )
            assertNull(claim(), "The leased job cannot be claimed twice")
            outbox.complete(job)
            assertEquals(0, rowCount())
        }

        @Test
        fun `draw creates one independently recoverable job per distinct recipient`() {
            publish(drawn(1, listOf("a@test.com", "b@test.com", "a@test.com")))
            val jobs = listOf(checkNotNull(claim()), checkNotNull(claim()))
            assertEquals(
                setOf("a@test.com", "b@test.com"),
                jobs
                    .map {
                        assertEquals(NotificationType.DRAW_RESULT, it.type)
                        json.readValue(it.payload, DrawResultPayload::class.java).recipientEmail
                    }.toSet()
            )
            assertNull(claim())
        }

        @Test
        fun `expired lease is recovered and a stale worker cannot acknowledge or reschedule it`() {
            publish(invitation)
            val original = checkNotNull(claim())
            makeAvailable()
            val recovered = checkNotNull(claim())
            assertEquals(original.id, recovered.id)
            assertNotEquals(original.claimId, recovered.claimId)
            outbox.complete(original)
            outbox.retry(original, "Stale failure", retryDelay)
            outbox.fail(original, "Stale failure")
            assertEquals(1, rowCount())
            assertNull(jdbc.queryForObject("SELECT last_error FROM notification_outbox", String::class.java))
            assertNull(jdbc.queryForObject("SELECT failed_at FROM notification_outbox", java.time.OffsetDateTime::class.java))
            outbox.complete(recovered)
            assertEquals(0, rowCount())
        }

        @Test
        fun `a renewed lease keeps the job from other workers, and only the current claim can renew it`() {
            publish(invitation)
            val first = checkNotNull(claim())
            makeAvailable()
            assertTrue(outbox.renew(first, lease))
            assertNull(claim(), "A renewed job is not claimed again")

            makeAvailable()
            val second = checkNotNull(claim())
            assertFalse(outbox.renew(first, lease), "A claim that lost its lease cannot renew it")
            assertTrue(outbox.renew(second, lease))

            outbox.complete(second)
            assertFalse(outbox.renew(second, lease), "A completed job has no lease left")

            publish(invitation)
            val failed = checkNotNull(claim())
            outbox.fail(failed, "Gave up")
            assertFalse(outbox.renew(failed, lease), "A failed job is never claimed again, so not renewed either")
        }

        @Test
        fun `parallel workers claim different jobs`() {
            repeat(2) { publish(invitation) }
            Executors.newFixedThreadPool(2).use { executor ->
                val tasks = List(2) { executor.submit<UUID> { checkNotNull(claim()).id } }
                assertEquals(2, tasks.map { it.get(5, TimeUnit.SECONDS) }.toSet().size)
            }
        }

        @Test
        fun `failed delivery is retained and retried later without affecting another recipient`() {
            val mail = mockk<EmailService>(relaxed = true)
            every { mail.sendVoteInvitation("recipient@test.com", any(), any(), any(), any()) } throws IllegalStateException("SMTP down")
            publish(invitation)
            publish(invitation.copy(recipientEmail = Email.of("good@test.com")))
            val worker = worker(mail)
            worker.poll()
            verify(exactly = 1) { mail.sendVoteInvitation("good@test.com", any(), any(), any(), any()) }
            assertEquals(1, rowCount())
            assertEquals("SMTP down", jdbc.queryForObject("SELECT last_error FROM notification_outbox", String::class.java))
            worker.poll()
            verify(exactly = 1) { mail.sendVoteInvitation("recipient@test.com", any(), any(), any(), any()) }
            makeAvailable()
            every { mail.sendVoteInvitation("recipient@test.com", any(), any(), any(), any()) } returns Unit
            worker.poll()
            assertEquals(0, rowCount())
        }

        @Test
        fun `worker deserializes and delivers draw results`() {
            val mail = mockk<EmailService>(relaxed = true)
            publish(drawn(3, listOf("a@test.com")))
            worker(mail).poll()
            verify(exactly = 1) { mail.sendDrawResult("a@test.com", "Vote", "Winner", "", 3, any(), any()) }
            assertEquals(0, rowCount())
        }

        @Test
        fun `retry reschedules the job after the given delay`() {
            publish(invitation)
            val job = checkNotNull(claim())
            outbox.retry(job, "x".repeat(2000), Duration.ofMinutes(30))
            val delaySeconds =
                checkNotNull(
                    jdbc.queryForObject(
                        "SELECT extract(epoch FROM available_at - now()) FROM notification_outbox",
                        Double::class.java
                    )
                )
            assertTrue(delaySeconds in 1700.0..1800.0, "available in ~30 minutes, was ${delaySeconds}s")
            assertEquals(1000, jdbc.queryForObject("SELECT length(last_error) FROM notification_outbox", Int::class.java))
        }

        @Test
        fun `failed jobs are kept for investigation, never claimed again and purged after retention`() {
            publish(invitation)
            outbox.fail(checkNotNull(claim()), "SMTP down")
            makeAvailable()
            assertNull(claim())
            assertEquals(1, rowCount())
            assertEquals(0, outbox.purgeFailed(Duration.ofDays(1)), "Recent failures are retained")
            jdbc.update("UPDATE notification_outbox SET failed_at = now() - interval '2 days'")
            assertEquals(1, outbox.purgeFailed(Duration.ofDays(1)))
            assertEquals(0, rowCount())
        }

        @Test
        fun `worker gives up after the configured number of attempts`() {
            val mail = mockk<EmailService>(relaxed = true)
            every { mail.sendVoteInvitation(any(), any(), any(), any(), any()) } throws IllegalStateException("SMTP down")
            publish(invitation)
            val worker = worker(mail, NotificationProperties(maxAttempts = 2))
            worker.poll()
            makeAvailable()
            worker.poll()
            makeAvailable()
            worker.poll()
            verify(exactly = 2) { mail.sendVoteInvitation(any(), any(), any(), any(), any()) }
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE failed_at IS NOT NULL", Int::class.java))
        }

        @Test
        fun `worker fails a job with an unreadable payload without sending`() {
            val mail = mockk<EmailService>(relaxed = true)
            outbox.append(NotificationType.INVITATION, """{"legacy":"format"}""")
            worker(mail).poll()
            verify(exactly = 0) { mail.sendVoteInvitation(any(), any(), any(), any(), any()) }
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE failed_at IS NOT NULL", Int::class.java))
        }

        @Test
        fun `poll dispatches at most twenty jobs per batch`() {
            val mail = mockk<EmailService>(relaxed = true)
            repeat(21) { publish(invitation) }
            worker(mail).poll()
            assertEquals(1, rowCount())
            verify(exactly = 20) { mail.sendVoteInvitation(any(), any(), any(), any(), any()) }
            worker(mail).poll()
            assertEquals(0, rowCount())
        }

        private fun claim() = outbox.claim(lease)

        private fun worker(
            mail: EmailService,
            properties: NotificationProperties = NotificationProperties(),
        ) = NotificationWorker(
            outbox,
            NotificationAdapter(mail, preferences, FrontendProperties("http://localhost:3000")),
            json,
            properties,
            SimpleMeterRegistry(),
            suppressions
        )

        private fun rowCount() = jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Int::class.java)

        private fun makeAvailable() {
            jdbc.update("UPDATE notification_outbox SET available_at = now() - interval '1 second'")
        }
    }
