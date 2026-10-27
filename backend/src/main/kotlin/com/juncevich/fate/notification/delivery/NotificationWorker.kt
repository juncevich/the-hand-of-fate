package com.juncevich.fate.notification.delivery

import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.NOTIFICATION_PAYLOAD_VERSION
import com.juncevich.fate.notification.NotificationPayload
import com.juncevich.fate.notification.port.EmailSuppressionPort
import com.juncevich.fate.notification.port.NotificationJob
import com.juncevich.fate.notification.port.NotificationOutboxPort
import com.juncevich.fate.notification.port.NotificationType
import com.juncevich.fate.shared.Email
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Delivers outbox jobs; safe to run on several backend instances. A claimed job is leased, and
 * while it is being delivered [renewLeases] keeps extending the lease, so a slow batch (SMTP
 * retries, waiting for a send permit) is not re-claimed and sent twice by another instance. The
 * lease only runs out when its worker died — then the job is delivered again (at least once).
 */
@Component
@ConditionalOnProperty(name = ["app.notifications.delivery-enabled"], havingValue = "true", matchIfMissing = true)
class NotificationWorker(
    private val outbox: NotificationOutboxPort,
    private val delivery: NotificationAdapter,
    private val json: JsonMapper,
    private val properties: NotificationProperties,
    private val meterRegistry: MeterRegistry,
    private val suppressions: EmailSuppressionPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Claimed jobs not yet acknowledged; their leases are renewed. */
    private val inFlight: MutableSet<NotificationJob> = ConcurrentHashMap.newKeySet()

    /** SMTP runs outside DB transactions, with bounded batches and per-recipient recovery. */
    @Scheduled(fixedDelayString = "\${app.notifications.poll-delay-ms:1000}")
    fun poll() {
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            repeat(properties.batchSize) {
                val job = outbox.claim(properties.lease) ?: return@use
                inFlight += job
                executor.execute {
                    try {
                        process(job)
                    } finally {
                        inFlight -= job
                    }
                }
            }
        }
    }

    /**
     * Extends the lease of every job still being delivered. Runs alongside a long [poll]: with
     * virtual threads Spring Boot's scheduler starts each scheduled run on a thread of its own.
     * A job whose lease is gone (acknowledged meanwhile, cancelled, or claimed again after this
     * instance stalled past the lease) is no longer renewed.
     */
    @Scheduled(fixedDelayString = "\${app.notifications.lease-renewal:30s}")
    fun renewLeases() {
        for (job in inFlight) {
            // Acknowledging removes the job first, so a lease lost here was really lost.
            if (!outbox.renew(job, properties.lease) && inFlight.remove(job)) {
                log.warn("Notification {} lost its lease while being delivered; it may be sent twice", job.id)
                meterRegistry.counter("notification.lease.lost", "type", job.type.metricTag).increment()
            }
        }
    }

    @Scheduled(fixedDelayString = "\${app.notifications.purge-delay-ms:3600000}")
    fun purgeFailed() {
        val purged = outbox.purgeFailed(properties.failedRetention)
        if (purged > 0) log.info("Purged {} failed notifications", purged)
    }

    // Recovery boundary: any delivery failure must be recorded on the job rather than lost.
    @Suppress("TooGenericExceptionCaught")
    private fun process(job: NotificationJob) {
        val payload =
            try {
                decode(job)
            } catch (ex: UnreadablePayloadException) {
                // Retrying can't fix an unreadable payload; give up right away.
                giveUp(job, ex)
                return
            }
        if (suppressions.isSuppressed(Email.trusted(payload.recipientEmail))) {
            // Checked at delivery rather than when queued, so unsubscribing also stops queued emails.
            meterRegistry.counter("notification.suppressed", "reason", "unsubscribed").increment()
            complete(job)
            return
        }
        try {
            send(payload)
        } catch (ex: Exception) {
            onDeliveryFailure(job, ex)
            return
        }
        complete(job)
    }

    // Each acknowledgement stops the job's lease renewal first (see renewLeases).
    private fun complete(job: NotificationJob) {
        inFlight -= job
        outbox.complete(job)
    }

    private fun decode(job: NotificationJob): NotificationPayload {
        val payload: NotificationPayload =
            try {
                when (job.type) {
                    NotificationType.INVITATION -> json.readValue<InvitationPayload>(job.payload)
                    NotificationType.DRAW_RESULT -> json.readValue<DrawResultPayload>(job.payload)
                }
            } catch (ex: JacksonException) {
                throw UnreadablePayloadException("Unreadable ${job.type} payload: ${ex.originalMessage}", ex)
            }
        if (payload.version != NOTIFICATION_PAYLOAD_VERSION) {
            throw UnreadablePayloadException("Unsupported ${job.type} payload version ${payload.version}")
        }
        return payload
    }

    private fun send(payload: NotificationPayload) {
        when (payload) {
            is InvitationPayload -> delivery.send(payload)
            is DrawResultPayload -> delivery.send(payload)
        }
    }

    private fun onDeliveryFailure(
        job: NotificationJob,
        ex: Exception,
    ) {
        meterRegistry.counter("notification.failed", "type", job.type.metricTag).increment()
        if (job.attempts >= properties.maxAttempts) {
            giveUp(job, ex)
            return
        }
        val delay = properties.retryDelay(job.attempts)
        log.warn(
            "Notification {} delivery attempt {}/{} failed, retrying in {}",
            job.id,
            job.attempts,
            properties.maxAttempts,
            delay,
            ex
        )
        inFlight -= job
        outbox.retry(job, ex.message ?: "Delivery failed", delay)
    }

    private fun giveUp(
        job: NotificationJob,
        ex: Exception,
    ) {
        log.error("Notification {} ({}) failed permanently after {} attempt(s)", job.id, job.type, job.attempts, ex)
        meterRegistry.counter("notification.dead", "type", job.type.metricTag).increment()
        inFlight -= job
        outbox.fail(job, ex.message ?: "Delivery failed")
    }

    private val NotificationType.metricTag: String
        get() =
            when (this) {
                NotificationType.INVITATION -> "invitation"
                NotificationType.DRAW_RESULT -> "draw-result"
            }
}

private class UnreadablePayloadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
