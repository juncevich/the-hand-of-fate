package com.juncevich.fate.notification.delivery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class NotificationPropertiesTest {
    private val properties = NotificationProperties()

    @Test
    fun `retry delay doubles from the base delay`() {
        assertEquals(
            listOf(1L, 2L, 4L, 8L, 16L).map(Duration::ofMinutes),
            (1..5).map(properties::retryDelay)
        )
    }

    @Test
    fun `retry delay is capped at the maximum delay`() {
        assertEquals(Duration.ofHours(6), properties.retryDelay(10))
        assertEquals(Duration.ofHours(6), properties.retryDelay(Int.MAX_VALUE))
    }

    @Test
    fun `retry delay never goes below the base delay`() {
        assertEquals(Duration.ofMinutes(1), properties.retryDelay(0))
    }

    @Test
    fun `rejects a non-positive attempt limit`() {
        assertThrows<IllegalArgumentException> { NotificationProperties(maxAttempts = 0) }
    }

    @Test
    fun `rejects a non-positive batch size`() {
        assertThrows<IllegalArgumentException> { NotificationProperties(batchSize = 0) }
    }

    @Test
    fun `lease renewal must come well before the lease runs out`() {
        assertThrows<IllegalArgumentException> { NotificationProperties(lease = Duration.ofMinutes(1), leaseRenewal = Duration.ofMinutes(1)) }
        assertThrows<IllegalArgumentException> { NotificationProperties(leaseRenewal = Duration.ZERO) }
    }
}
