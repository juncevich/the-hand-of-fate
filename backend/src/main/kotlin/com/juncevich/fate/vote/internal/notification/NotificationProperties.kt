package com.juncevich.fate.vote.internal.notification

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Outbox delivery policy. A failed job is retried with exponential backoff
 * ([retryBaseDelay] doubling up to [retryMaxDelay]); after [maxAttempts] it is marked failed,
 * counted in `notification.dead` and deleted once [failedRetention] has passed.
 */
@ConfigurationProperties(prefix = "app.notifications")
data class NotificationProperties(
    val deliveryEnabled: Boolean = true,
    /** Jobs claimed per poll. */
    val batchSize: Int = 20,
    val maxAttempts: Int = 10,
    /** How long a claimed job stays invisible to other pollers before it is considered abandoned. */
    val lease: Duration = Duration.ofMinutes(2),
    val retryBaseDelay: Duration = Duration.ofMinutes(1),
    val retryMaxDelay: Duration = Duration.ofHours(6),
    val failedRetention: Duration = Duration.ofDays(14),
) {
    init {
        require(batchSize > 0) { "app.notifications.batch-size must be positive" }
        require(maxAttempts > 0) { "app.notifications.max-attempts must be positive" }
    }

    /** Delay before the next attempt after attempt number [attempt] (1-based) failed. */
    fun retryDelay(attempt: Int): Duration {
        val exponent = (attempt - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        val delay = retryBaseDelay.multipliedBy(1L shl exponent)
        return if (delay > retryMaxDelay) retryMaxDelay else delay
    }

    private companion object {
        // 2^30 × any sane base delay already exceeds every realistic cap; also keeps the shift in range.
        const val MAX_BACKOFF_EXPONENT = 30
    }
}
