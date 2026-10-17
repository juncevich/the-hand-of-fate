package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.config.RateLimitProperties
import com.juncevich.fate.auth.internal.service.LoginThrottle
import com.juncevich.fate.auth.internal.service.LoginThrottledException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class LoginThrottleTest {
    private var now = 1_000_000L
    private val throttle =
        LoginThrottle(
            RateLimitProperties(loginFailuresPerAccount = 3, loginFailureWindowSeconds = 60),
            clock = { now }
        )

    @Test
    fun `blocks an account once its failure budget is spent, with the remaining window as retry-after`() {
        repeat(3) {
            assertDoesNotThrow { throttle.ensureAllowed("a@x.com") }
            throttle.recordFailure("a@x.com")
        }
        now += 15_500

        val ex = assertThrows<LoginThrottledException> { throttle.ensureAllowed("a@x.com") }

        assertEquals(45, ex.retryAfterSeconds)
    }

    @Test
    fun `tracks accounts independently`() {
        repeat(3) { throttle.recordFailure("a@x.com") }

        assertDoesNotThrow { throttle.ensureAllowed("b@x.com") }
    }

    @Test
    fun `allows the account again once the window has passed`() {
        repeat(3) { throttle.recordFailure("a@x.com") }
        now += 60_000

        assertDoesNotThrow { throttle.ensureAllowed("a@x.com") }
        throttle.recordFailure("a@x.com")
        assertDoesNotThrow { throttle.ensureAllowed("a@x.com") }
    }

    @Test
    fun `reset clears the failures`() {
        repeat(3) { throttle.recordFailure("a@x.com") }

        throttle.reset("a@x.com")

        assertDoesNotThrow { throttle.ensureAllowed("a@x.com") }
    }

    @Test
    fun `prunes expired entries when many accounts are tracked`() {
        repeat(10_000) { throttle.recordFailure("user$it@x.com") }
        now += 60_000

        throttle.recordFailure("fresh@x.com")
        repeat(2) { throttle.recordFailure("fresh@x.com") }

        assertThrows<LoginThrottledException> { throttle.ensureAllowed("fresh@x.com") }
        assertDoesNotThrow { throttle.ensureAllowed("user0@x.com") }
    }

    @Test
    fun `does nothing when rate limiting is disabled`() {
        val disabled = LoginThrottle(RateLimitProperties(enabled = false, loginFailuresPerAccount = 1), clock = { now })
        repeat(5) { disabled.recordFailure("a@x.com") }

        assertDoesNotThrow { disabled.ensureAllowed("a@x.com") }
    }
}
