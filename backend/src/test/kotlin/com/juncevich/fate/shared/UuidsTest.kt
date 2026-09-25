package com.juncevich.fate.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UuidsTest {
    @Test
    fun `generates RFC 9562 version 7 UUIDs`() {
        val id = uuidV7()
        assertEquals(7, id.version())
        assertEquals(2, id.variant())
    }

    @Test
    fun `embeds the current Unix time in milliseconds`() {
        val before = System.currentTimeMillis()
        val id = uuidV7()
        val after = System.currentTimeMillis()

        val embedded = id.mostSignificantBits ushr 16
        assertTrue(embedded in before..after, "timestamp $embedded not in [$before, $after]")
    }

    @Test
    fun `ids created in later milliseconds sort after earlier ones`() {
        val earlier = uuidV7()
        Thread.sleep(2)
        val later = uuidV7()

        // PostgreSQL orders uuid bytewise (unsigned); compare the same way
        assertTrue(earlier.toString() < later.toString())
        assertTrue(java.lang.Long.compareUnsigned(earlier.mostSignificantBits, later.mostSignificantBits) < 0)
    }

    @Test
    fun `ids are unique`() {
        val ids = List(10_000) { uuidV7() }
        assertEquals(ids.size, ids.toSet().size)
    }
}
