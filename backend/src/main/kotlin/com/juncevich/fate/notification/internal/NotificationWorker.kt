package com.juncevich.fate.notification.internal

import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.EmailVerificationPayload
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.NOTIFICATION_PAYLOAD_VERSION
import com.juncevich.fate.notification.NotificationPayload
import com.juncevich.fate.notification.PasswordResetPayload
import com.juncevich.fate.notification.internal.port.EmailSuppressionPort
import com.juncevich.fate.notification.internal.port.NotificationJob
import com.juncevich.fate.notification.internal.port.NotificationOutboxPort
import com.juncevich.fate.notification.internal.port.NotificationType
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.Executors

/**
 * Delivers outbox jobs. Designed for a single backend instance: a batch can outlive the lease
 * under heavy SMTP retries, and a second instance would then re-claim and send duplicates.
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

    /** SMTP runs outside DB transactions, with bounded batches and per-recipient recovery. */
    @Scheduled(fixedDelayString = "\${app.notifications.poll-delay-ms:1000}")
    fun poll() {
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            repeat(properties.batchSize) {
                val job = outbox.claim(properties.lease) ?: return@use
                executor.execute { process(job) }
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
        if (payload.isOptOutable && suppressions.isSuppressed(payload.recipientEmail)) {
            // Checked at delivery rather than when queued, so unsubscribing also stops queued emails.
            meterRegistry.counter("notification.suppressed", "reason", "unsubscribed").increment()
            outbox.complete(job)
            return
        }
        try {
            send(payload)
        } catch (ex: Exception) {
            onDeliveryFailure(job, ex)
            return
        }
        outbox.complete(job)
    }

    private fun decode(job: NotificationJob): NotificationPayload {
        val payload: NotificationPayload =
            try {
                when (job.type) {
                    NotificationType.INVITATION -> {
                        json.readValue(job.payload, InvitationPayload::class.java)
                    }

                    NotificationType.DRAW_RESULT -> {
                        json.readValue(job.payload, DrawResultPayload::class.java)
                    }

                    NotificationType.EMAIL_VERIFICATION -> {
                        json.readValue(
                            job.payload,
                            EmailVerificationPayload::class.java
                        )
                    }

                    NotificationType.PASSWORD_RESET -> {
                        json.readValue(job.payload, PasswordResetPayload::class.java)
                    }
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
            is EmailVerificationPayload -> delivery.send(payload)
            is PasswordResetPayload -> delivery.send(payload)
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
        outbox.retry(job, ex.message ?: "Delivery failed", delay)
    }

    private fun giveUp(
        job: NotificationJob,
        ex: Exception,
    ) {
        log.error("Notification {} ({}) failed permanently after {} attempt(s)", job.id, job.type, job.attempts, ex)
        meterRegistry.counter("notification.dead", "type", job.type.metricTag).increment()
        outbox.fail(job, ex.message ?: "Delivery failed")
    }

    /** Notification emails can be unsubscribed from; account emails (verification, reset) cannot. */
    private val NotificationPayload.isOptOutable: Boolean
        get() =
            when (this) {
                is InvitationPayload, is DrawResultPayload -> true
                is EmailVerificationPayload, is PasswordResetPayload -> false
            }

    private val NotificationType.metricTag: String
        get() =
            when (this) {
                NotificationType.INVITATION -> "invitation"
                NotificationType.DRAW_RESULT -> "draw-result"
                NotificationType.EMAIL_VERIFICATION -> "email-verification"
                NotificationType.PASSWORD_RESET -> "password-reset"
            }
}

private class UnreadablePayloadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
