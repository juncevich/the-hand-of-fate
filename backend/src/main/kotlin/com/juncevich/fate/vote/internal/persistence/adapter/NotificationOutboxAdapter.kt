package com.juncevich.fate.vote.internal.persistence.adapter

import com.juncevich.fate.shared.uuidV7
import com.juncevich.fate.vote.internal.port.NotificationJob
import com.juncevich.fate.vote.internal.port.NotificationOutboxPort
import com.juncevich.fate.vote.internal.port.NotificationType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

private const val MAX_ERROR_LENGTH = 1000
private const val MILLIS_PER_SECOND = 1000.0

@Component
class NotificationOutboxAdapter(
    private val jdbc: JdbcTemplate,
) : NotificationOutboxPort {
    override fun append(
        type: NotificationType,
        payload: String,
    ) {
        jdbc.update(
            "INSERT INTO notification_outbox (id, event_type, payload) VALUES (?, ?, ?)",
            uuidV7(),
            type.name,
            payload
        )
    }

    /** Single atomic statement; the lease allows recovery after a worker crashes. */
    override fun claim(lease: Duration): NotificationJob? =
        jdbc
            .query(
                """
            WITH candidate AS (
                SELECT id FROM notification_outbox
                WHERE failed_at IS NULL AND available_at <= now()
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE notification_outbox o
            SET attempts = attempts + 1, claim_id = ?, available_at = now() + make_interval(secs => ?)
            FROM candidate c WHERE o.id = c.id
            RETURNING o.id, o.event_type, o.payload, o.claim_id, o.attempts
            """,
                { row, _ ->
                    NotificationJob(
                        row.getObject("id", UUID::class.java),
                        NotificationType.valueOf(row.getString("event_type")),
                        row.getString("payload"),
                        row.getObject("claim_id", UUID::class.java),
                        row.getInt("attempts")
                    )
                },
                UUID.randomUUID(),
                lease.toMillis() / MILLIS_PER_SECOND
            ).firstOrNull()

    override fun complete(job: NotificationJob) {
        jdbc.update("DELETE FROM notification_outbox WHERE id = ? AND claim_id = ?", job.id, job.claimId)
    }

    override fun retry(
        job: NotificationJob,
        error: String,
        delay: Duration,
    ) {
        jdbc.update(
            """
            UPDATE notification_outbox SET available_at = now() + make_interval(secs => ?), last_error = ?
            WHERE id = ? AND claim_id = ?
            """,
            delay.toMillis() / MILLIS_PER_SECOND,
            error.take(MAX_ERROR_LENGTH),
            job.id,
            job.claimId
        )
    }

    override fun fail(
        job: NotificationJob,
        error: String,
    ) {
        jdbc.update(
            "UPDATE notification_outbox SET failed_at = now(), last_error = ? WHERE id = ? AND claim_id = ?",
            error.take(MAX_ERROR_LENGTH),
            job.id,
            job.claimId
        )
    }

    override fun purgeFailed(retention: Duration): Int =
        jdbc.update(
            "DELETE FROM notification_outbox WHERE failed_at < now() - make_interval(secs => ?)",
            retention.toMillis() / MILLIS_PER_SECOND
        )
}
