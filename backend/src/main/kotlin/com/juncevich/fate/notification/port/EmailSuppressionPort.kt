package com.juncevich.fate.notification.port

import com.juncevich.fate.shared.Email

interface EmailSuppressionPort {
    fun isSuppressed(email: Email): Boolean

    /** Idempotent. */
    fun suppress(email: Email)

    fun allow(email: Email)
}
