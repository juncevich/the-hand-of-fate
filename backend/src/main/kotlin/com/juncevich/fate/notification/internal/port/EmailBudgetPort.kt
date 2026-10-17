package com.juncevich.fate.notification.internal.port

import java.time.Duration
import java.util.UUID

interface EmailBudgetPort {
    /** Serializes budget checks of [userId] until the current transaction ends. */
    fun lock(userId: UUID)

    /** Emails recorded for [userId] within the last [window]. */
    fun usedWithin(
        userId: UUID,
        window: Duration,
    ): Int

    fun record(
        userId: UUID,
        recipients: Int,
    )

    /** Deletes entries older than [window]; returns how many were removed. */
    fun purgeOlderThan(window: Duration): Int
}
