package com.juncevich.fate.vote.internal.port

import java.time.Duration
import java.util.UUID

enum class NotificationType { INVITATION, DRAW_RESULT }

data class NotificationJob(
    val id: UUID,
    val type: NotificationType,
    val payload: String,
    val claimId: UUID,
    /** Number of claims so far, including the current one. */
    val attempts: Int,
)

interface NotificationOutboxPort {
    fun append(
        type: NotificationType,
        payload: String,
    )

    /** Leases the oldest pending job for [lease]; `null` when nothing is due. */
    fun claim(lease: Duration): NotificationJob?

    fun complete(job: NotificationJob)

    fun retry(
        job: NotificationJob,
        error: String,
        delay: Duration,
    )

    /** Gives up on the job; it is kept for investigation but never claimed again. */
    fun fail(
        job: NotificationJob,
        error: String,
    )

    /** Deletes failed jobs older than [retention]; returns how many were removed. */
    fun purgeFailed(retention: Duration): Int
}
