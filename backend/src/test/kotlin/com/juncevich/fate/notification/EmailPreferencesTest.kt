package com.juncevich.fate.notification

import com.juncevich.fate.notification.internal.port.EmailSuppressionPort
import com.juncevich.fate.shared.BadRequestException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EmailPreferencesTest {
    private val suppressions = mockk<EmailSuppressionPort>(relaxed = true)
    private val preferences = EmailPreferences(suppressions, "test-unsubscribe-secret")

    @Test
    fun `a correctly signed link unsubscribes the normalized address`() {
        preferences.unsubscribe(" Ann@Test.com ", preferences.sign("ann@test.com").uppercase())

        verify { suppressions.suppress("ann@test.com") }
    }

    @Test
    fun `a forged or foreign signature is refused`() {
        listOf("deadbeef", preferences.sign("someone-else@test.com")).forEach { signature ->
            val ex = assertThrows<BadRequestException> { preferences.unsubscribe("ann@test.com", signature) }
            assertEquals("INVALID_TOKEN", ex.code)
        }
        verify(exactly = 0) { suppressions.suppress(any()) }
    }

    @Test
    fun `signatures depend on the secret`() {
        assertNotEquals(
            preferences.sign("ann@test.com"),
            EmailPreferences(suppressions, "another-secret").sign("ann@test.com")
        )
    }

    @Test
    fun `the settings toggle suppresses or allows the account's own address`() {
        every { suppressions.isSuppressed("ann@test.com") } returns true
        assertFalse(preferences.receivesNotifications("ANN@test.com"))

        preferences.setReceivesNotifications("ann@test.com", enabled = true)
        preferences.setReceivesNotifications("ann@test.com", enabled = false)

        verify { suppressions.allow("ann@test.com") }
        verify { suppressions.suppress("ann@test.com") }
        every { suppressions.isSuppressed("ann@test.com") } returns false
        assertTrue(preferences.receivesNotifications("ann@test.com"))
    }
}
