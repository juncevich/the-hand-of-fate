package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.internal.config.RateLimitProperties
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

private const val MILLIS_PER_SECOND = 1000L
private const val MAX_TRACKED_ACCOUNTS = 10_000

/**
 * Per-account brute-force guard that complements the per-IP `RateLimitFilter`, which an
 * attacker rotating addresses bypasses. Failed logins are counted per normalized email in a
 * fixed window, for unknown addresses too, so throttling never reveals whether an account
 * exists. In-memory like the IP limiter: adequate for a single instance only.
 */
@Component
class LoginThrottle(
    private val properties: RateLimitProperties,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val failures = ConcurrentHashMap<String, Failures>()

    private val windowMillis get() = properties.loginFailureWindowSeconds * MILLIS_PER_SECOND

    /** @throws LoginThrottledException once the account has used up its failed-attempt budget. */
    fun ensureAllowed(email: String) {
        if (!properties.enabled) return
        val current = failures[email] ?: return
        val now = clock()
        if (current.count >= properties.loginFailuresPerAccount && !current.expiredAt(now)) {
            val remainingMillis = current.startedAt + windowMillis - now
            throw LoginThrottledException((remainingMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)
        }
    }

    fun recordFailure(email: String) {
        if (!properties.enabled) return
        val now = clock()
        pruneIfNeeded(now)
        failures.compute(email) { _, current ->
            if (current == null || current.expiredAt(now)) Failures(now, 1) else current.copy(count = current.count + 1)
        }
    }

    fun reset(email: String) {
        failures.remove(email)
    }

    /** Keep the map from growing unbounded when attempts are spread over many addresses. */
    private fun pruneIfNeeded(now: Long) {
        if (failures.size < MAX_TRACKED_ACCOUNTS) return
        failures.entries.removeIf { it.value.expiredAt(now) }
    }

    private fun Failures.expiredAt(now: Long) = now >= startedAt + windowMillis

    private data class Failures(
        val startedAt: Long,
        val count: Int,
    )
}

class LoginThrottledException(
    val retryAfterSeconds: Long,
) : RuntimeException("Too many failed login attempts")
