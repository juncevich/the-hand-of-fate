package com.juncevich.fate.notification.persistence

import com.juncevich.fate.notification.port.NotificationJob
import com.juncevich.fate.notification.port.NotificationOutboxPort
import com.juncevich.fate.notification.port.NotificationType
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.uuidV7
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

private const val MAX_ERROR_LENGTH = 1000

@Component
class NotificationOutboxAdapter(
    private val jdbc: JdbcClient,
) : NotificationOutboxPort {
    override fun append(
        type: NotificationType,
        payload: String,
    ) {
        jdbc
            .sql("INSERT INTO notification_outbox (id, event_type, payload) VALUES (:id, :type, :payload)")
            .param("id", uuidV7())
            .param("type", type.name)
            .param("payload", payload)
            .update()
    }

    /** Single atomic statement; the lease allows recovery after a worker crashes. */
    override fun claim(lease: Duration): NotificationJob? =
        jdbc
            .sql(
                """
                WITH candidate AS (
                    SELECT id FROM notification_outbox
                    WHERE failed_at IS NULL AND available_at <= now()
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE notification_outbox o
                SET attempts = attempts + 1, claim_id = :claimId, available_at = now() + make_interval(secs => :lease)
                FROM candidate c WHERE o.id = c.id
                RETURNING o.id, o.event_type, o.payload, o.claim_id, o.attempts
                """
            ).param("claimId", UUID.randomUUID())
            .param("lease", lease.seconds())
            .query { row, _ ->
                NotificationJob(
                    row.getObject("id", UUID::class.java),
                    NotificationType.valueOf(row.getString("event_type")),
                    row.getString("payload"),
                    row.getObject("claim_id", UUID::class.java),
                    row.getInt("attempts")
                )
            }.optional()
            .orElse(null)

    override fun renew(
        job: NotificationJob,
        lease: Duration,
    ): Boolean =
        jdbc
            .sql(
                """
                UPDATE notification_outbox SET available_at = now() + make_interval(secs => :lease)
                WHERE id = :id AND claim_id = :claimId AND failed_at IS NULL
                """
            ).param("lease", lease.seconds())
            .claimed(job)
            .update() == 1

    override fun complete(job: NotificationJob) {
        jdbc
            .sql("DELETE FROM notification_outbox WHERE id = :id AND claim_id = :claimId")
            .claimed(job)
            .update()
    }

    override fun retry(
        job: NotificationJob,
        error: String,
        delay: Duration,
    ) {
        jdbc
            .sql(
                """
                UPDATE notification_outbox SET available_at = now() + make_interval(secs => :delay), last_error = :error
                WHERE id = :id AND claim_id = :claimId
                """
            ).param("delay", delay.seconds())
            .param("error", error.take(MAX_ERROR_LENGTH))
            .claimed(job)
            .update()
    }

    override fun fail(
        job: NotificationJob,
        error: String,
    ) {
        jdbc
            .sql(
                """
                UPDATE notification_outbox SET failed_at = now(), last_error = :error
                WHERE id = :id AND claim_id = :claimId
                """
            ).param("error", error.take(MAX_ERROR_LENGTH))
            .claimed(job)
            .update()
    }

    override fun deleteForRecipient(email: Email) {
        jdbc
            .sql("DELETE FROM notification_outbox WHERE payload::jsonb ->> 'recipientEmail' = :email")
            .param("email", email.value)
            .update()
    }

    override fun purgeFailed(retention: Duration): Int =
        jdbc
            .sql("DELETE FROM notification_outbox WHERE failed_at < now() - make_interval(secs => :retention)")
            .param("retention", retention.seconds())
            .update()
}

/** Fences a write to the claim that delivered [job]: a job claimed again elsewhere is left alone. */
private fun JdbcClient.StatementSpec.claimed(job: NotificationJob) = param("id", job.id).param("claimId", job.claimId)
