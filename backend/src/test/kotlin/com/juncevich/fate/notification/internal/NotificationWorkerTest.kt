package com.juncevich.fate.notification.internal

import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.EmailVerificationPayload
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.NOTIFICATION_PAYLOAD_VERSION
import com.juncevich.fate.notification.PasswordResetPayload
import com.juncevich.fate.notification.internal.port.EmailSuppressionPort
import com.juncevich.fate.notification.internal.port.NotificationJob
import com.juncevich.fate.notification.internal.port.NotificationOutboxPort
import com.juncevich.fate.notification.internal.port.NotificationType
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Duration
import java.util.UUID

class NotificationWorkerTest {
    private val outbox = mockk<NotificationOutboxPort>(relaxed = true)
    private val delivery = mockk<NotificationAdapter>()
    private val json = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val properties = NotificationProperties(maxAttempts = 3)
    private val meterRegistry = SimpleMeterRegistry()
    private val suppressions = mockk<EmailSuppressionPort> { every { isSuppressed(any()) } returns false }
    private val worker = NotificationWorker(outbox, delivery, json, properties, meterRegistry, suppressions)

    private val invitation = InvitationPayload(UUID.randomUUID(), "Vote", "Creator", "p@test.com")

    private fun job(
        attempts: Int = 1,
        type: NotificationType = NotificationType.INVITATION,
        payload: String = json.writeValueAsString(invitation),
    ) = NotificationJob(UUID.randomUUID(), type, payload, UUID.randomUUID(), attempts)

    /** Makes the next poll process exactly [job]. */
    private fun queue(job: NotificationJob) {
        every { outbox.claim(properties.lease) } returns job andThen null
    }

    private fun count(name: String) =
        meterRegistry
            .find(name)
            .tag("type", "invitation")
            .counter()
            ?.count() ?: 0.0

    @Test
    fun `delivered job is completed`() {
        val job = job()
        queue(job)
        every { delivery.send(invitation) } just runs

        worker.poll()

        verify(exactly = 1) { outbox.complete(job) }
        verify(exactly = 0) { outbox.retry(any(), any(), any()) }
        assertEquals(0.0, count("notification.failed"))
    }

    @Test
    fun `failed attempt is rescheduled with exponential backoff`() {
        val job = job(attempts = 2)
        queue(job)
        every { delivery.send(invitation) } throws IllegalStateException("SMTP down")

        worker.poll()

        verify(exactly = 1) { outbox.retry(job, "SMTP down", Duration.ofMinutes(2)) }
        verify(exactly = 0) { outbox.complete(any()) }
        verify(exactly = 0) { outbox.fail(any(), any()) }
        assertEquals(1.0, count("notification.failed"))
        assertEquals(0.0, count("notification.dead"))
    }

    @Test
    fun `last failed attempt marks the job failed and counts it as dead`() {
        val job = job(attempts = properties.maxAttempts)
        queue(job)
        every { delivery.send(invitation) } throws IllegalStateException("SMTP down")

        worker.poll()

        verify(exactly = 1) { outbox.fail(job, "SMTP down") }
        verify(exactly = 0) { outbox.retry(any(), any(), any()) }
        assertEquals(1.0, count("notification.failed"))
        assertEquals(1.0, count("notification.dead"))
    }

    @Test
    fun `unreadable payload fails immediately without delivery`() {
        val job = job(payload = """{"unexpected":true}""")
        queue(job)

        worker.poll()

        verify(exactly = 1) { outbox.fail(job, match { it.startsWith("Unreadable INVITATION payload") }) }
        verify(exactly = 0) { delivery.send(any<InvitationPayload>()) }
        assertEquals(1.0, count("notification.dead"))
    }

    @Test
    fun `payload of an unsupported version fails immediately without delivery`() {
        val job = job(payload = json.writeValueAsString(invitation.copy(version = NOTIFICATION_PAYLOAD_VERSION + 1)))
        queue(job)

        worker.poll()

        verify(exactly = 1) { outbox.fail(job, "Unsupported INVITATION payload version 2") }
        verify(exactly = 0) { delivery.send(any<InvitationPayload>()) }
    }

    @Test
    fun `draw result payload is routed to the draw result sender`() {
        val payload = DrawResultPayload(UUID.randomUUID(), "Vote", "p@test.com", "Winner", null, 3)
        val job = job(type = NotificationType.DRAW_RESULT, payload = json.writeValueAsString(payload))
        queue(job)
        every { delivery.send(payload) } just runs

        worker.poll()

        verify(exactly = 1) { delivery.send(payload) }
        verify(exactly = 1) { outbox.complete(job) }
    }

    @Test
    fun `account emails are routed to their senders`() {
        val verification = EmailVerificationPayload("a@test.com", "Ann", "tok-1")
        val reset = PasswordResetPayload("a@test.com", "Ann", "tok-2")
        val first = job(type = NotificationType.EMAIL_VERIFICATION, payload = json.writeValueAsString(verification))
        val second = job(type = NotificationType.PASSWORD_RESET, payload = json.writeValueAsString(reset))
        every { outbox.claim(properties.lease) } returns first andThen second andThen null
        every { delivery.send(verification) } just runs
        every { delivery.send(reset) } just runs

        worker.poll()

        verify(exactly = 1) { delivery.send(verification) }
        verify(exactly = 1) { delivery.send(reset) }
        verify(exactly = 1) { outbox.complete(first) }
        verify(exactly = 1) { outbox.complete(second) }
    }

    @Test
    fun `an unsubscribed address gets no notification email, the job is done`() {
        val job = job()
        queue(job)
        every { suppressions.isSuppressed("p@test.com") } returns true

        worker.poll()

        verify(exactly = 0) { delivery.send(any<InvitationPayload>()) }
        verify(exactly = 1) { outbox.complete(job) }
        assertEquals(1.0, meterRegistry.counter("notification.suppressed", "reason", "unsubscribed").count())
    }

    @Test
    fun `account emails still reach an unsubscribed address`() {
        val payload = PasswordResetPayload("p@test.com", "Ann", "tok")
        val job = job(type = NotificationType.PASSWORD_RESET, payload = json.writeValueAsString(payload))
        queue(job)
        every { suppressions.isSuppressed("p@test.com") } returns true
        every { delivery.send(payload) } just runs

        worker.poll()

        verify(exactly = 1) { delivery.send(payload) }
    }

    @Test
    fun `purge removes failed jobs older than the retention`() {
        every { outbox.purgeFailed(properties.failedRetention) } returns 2

        worker.purgeFailed()

        verify(exactly = 1) { outbox.purgeFailed(Duration.ofDays(14)) }
    }
}
