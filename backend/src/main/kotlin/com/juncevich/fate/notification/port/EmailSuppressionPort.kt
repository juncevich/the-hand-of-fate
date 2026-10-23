package com.juncevich.fate.notification.port

interface EmailSuppressionPort {
    fun isSuppressed(email: String): Boolean

    /** Idempotent. */
    fun suppress(email: String)

    fun allow(email: String)
}
