package com.juncevich.fate.notification

import com.juncevich.fate.notification.port.EmailSuppressionPort
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.ErrorCode
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
        preferences.unsubscribe(" Ann@Test.com ", preferences.sign(Email.of("ann@test.com")).uppercase())

        verify { suppressions.suppress(Email.of("ann@test.com")) }
    }

    @Test
    fun `a forged or foreign signature is refused`() {
        listOf("deadbeef", preferences.sign(Email.of("someone-else@test.com"))).forEach { signature ->
            val ex = assertThrows<BadRequestException> { preferences.unsubscribe("ann@test.com", signature) }
            assertEquals(ErrorCode.INVALID_TOKEN, ex.code)
        }
        verify(exactly = 0) { suppressions.suppress(any()) }
    }

    @Test
    fun `signatures depend on the secret`() {
        assertNotEquals(
            preferences.sign(Email.of("ann@test.com")),
            EmailPreferences(suppressions, "another-secret").sign(Email.of("ann@test.com"))
        )
    }

    @Test
    fun `the settings toggle suppresses or allows the account's own address`() {
        every { suppressions.isSuppressed(Email.of("ann@test.com")) } returns true
        assertFalse(preferences.receivesNotifications(Email.of("ANN@test.com")))

        preferences.setReceivesNotifications(Email.of("ann@test.com"), enabled = true)
        preferences.setReceivesNotifications(Email.of("ann@test.com"), enabled = false)

        verify { suppressions.allow(Email.of("ann@test.com")) }
        verify { suppressions.suppress(Email.of("ann@test.com")) }
        every { suppressions.isSuppressed(Email.of("ann@test.com")) } returns false
        assertTrue(preferences.receivesNotifications(Email.of("ann@test.com")))
    }
}
