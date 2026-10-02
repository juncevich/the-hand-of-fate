package com.juncevich.fate.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EmailValidationTest {
    @Test
    fun `canonical addresses have no surrounding spaces and are lowercase`() {
        assertEquals("user@test.com", normalizeEmail(" USER@Test.Com "))
    }

    @Test
    fun `malformed and oversized addresses are rejected before persistence`() {
        for (email in listOf("invalid", "a b@test.com", "a".repeat(256) + "@test.com")) {
            assertFalse(isValidEmail(email))
            assertThrows<BadRequestException> { normalizeEmail(email) }
        }
    }
}
