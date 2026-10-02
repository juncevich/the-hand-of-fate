package com.juncevich.fate.vote.internal.notification

import com.juncevich.fate.vote.internal.port.NotificationJob
import com.juncevich.fate.vote.internal.port.NotificationOutboxPort
import com.juncevich.fate.vote.internal.port.NotificationType
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
    private val worker = NotificationWorker(outbox, delivery, json, properties, meterRegistry)

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
    fun `purge removes failed jobs older than the retention`() {
        every { outbox.purgeFailed(properties.failedRetention) } returns 2

        worker.purgeFailed()

        verify(exactly = 1) { outbox.purgeFailed(Duration.ofDays(14)) }
    }
}
