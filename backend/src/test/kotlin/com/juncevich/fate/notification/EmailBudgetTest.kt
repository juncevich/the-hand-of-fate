package com.juncevich.fate.notification

import com.juncevich.fate.notification.EmailBudget
import com.juncevich.fate.notification.internal.NotificationProperties
import com.juncevich.fate.notification.internal.port.EmailBudgetPort
import com.juncevich.fate.shared.TooManyRequestsException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.UUID

class EmailBudgetTest {
    private val port = mockk<EmailBudgetPort>()
    private val meterRegistry = SimpleMeterRegistry()
    private val properties = NotificationProperties(emailBudgetPerUser = 10, emailBudgetWindow = Duration.ofHours(24))
    private val budget = EmailBudget(port, properties, meterRegistry)
    private val userId = UUID.randomUUID()

    private fun used(count: Int) {
        every { port.lock(userId) } just runs
        every { port.usedWithin(userId, Duration.ofHours(24)) } returns count
        every { port.record(userId, any()) } just runs
    }

    @Test
    fun `reserves under the user lock when the emails fit the budget`() {
        used(7)

        assertTrue(budget.tryReserve(userId, 3))

        verifyOrder {
            port.lock(userId)
            port.usedWithin(userId, Duration.ofHours(24))
            port.record(userId, 3)
        }
    }

    @Test
    fun `refuses without recording anything when the emails would exceed the budget`() {
        used(8)

        assertFalse(budget.tryReserve(userId, 3))

        verify(exactly = 0) { port.record(any(), any()) }
        assertEquals(3.0, meterRegistry.counter("notification.suppressed").count())
    }

    @Test
    fun `reserveOrThrow rejects the action over the budget`() {
        used(10)

        val ex = assertThrows<TooManyRequestsException> { budget.reserveOrThrow(userId, 1) }

        assertTrue(ex.message!!.contains("at most 10"))
    }

    @Test
    fun `reserveOrThrow passes within the budget`() {
        used(0)

        assertDoesNotThrow { budget.reserveOrThrow(userId, 10) }
        verify { port.record(userId, 10) }
    }

    @Test
    fun `nothing to send needs no budget and takes no lock`() {
        assertTrue(budget.tryReserve(userId, 0))

        verify(exactly = 0) { port.lock(any()) }
    }

    @Test
    fun `purges entries past the window`() {
        every { port.purgeOlderThan(Duration.ofHours(24)) } returns 4

        budget.purgeExpired()

        verify { port.purgeOlderThan(Duration.ofHours(24)) }
    }
}
