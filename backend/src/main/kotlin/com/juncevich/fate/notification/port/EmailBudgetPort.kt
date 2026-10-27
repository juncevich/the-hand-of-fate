package com.juncevich.fate.notification.port

import com.juncevich.fate.shared.UserId
import java.time.Duration

interface EmailBudgetPort {
    /** Serializes budget checks of [userId] until the current transaction ends. */
    fun lock(userId: UserId)

    /** Emails recorded for [userId] within the last [window]. */
    fun usedWithin(
        userId: UserId,
        window: Duration,
    ): Int

    fun record(
        userId: UserId,
        recipients: Int,
    )

    /** Deletes entries older than [window]; returns how many were removed. */
    fun purgeOlderThan(window: Duration): Int
}
