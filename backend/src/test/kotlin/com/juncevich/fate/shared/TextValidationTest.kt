package com.juncevich.fate.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TextValidationTest {
    @Test
    fun `a title is trimmed and may hold any printable text`() {
        assertEquals("Обед 🍕 «пятница»", requireValidTitle("  Обед 🍕 «пятница»\n"))
        assertEquals("x".repeat(255), requireValidTitle("x".repeat(255)))
    }

    @Test
    fun `a title is one line of 1 to 255 characters`() {
        listOf("", "   ", "x".repeat(256), "Lunch\nBcc: x@evil.test", "Lu\rnch", "Tab\there", "Null\u0000byte").forEach { title ->
            assertEquals(ErrorCode.TITLE_INVALID, assertThrows<BadRequestException>("for ${title.take(20)}") { requireValidTitle(title) }.code)
        }
    }
}
