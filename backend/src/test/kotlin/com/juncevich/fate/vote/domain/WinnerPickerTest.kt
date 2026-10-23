package com.juncevich.fate.vote.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WinnerPickerTest {
    private val picker = WinnerPicker()

    // Computed by an independent implementation (Python hashlib). Changing the algorithm would
    // make every stored draw unverifiable, so these values must never change.
    @ParameterizedTest
    @CsvSource(
        "0000000000000000000000000000000000000000000000000000000000000000, 0, 1, 1, 5, 43",
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff, 0, 1, 1, 5, 55",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef, 0, 0, 2, 3, 76"
    )
    fun `index is a fixed function of seed and size`(
        seed: String,
        of1: Int,
        of2: Int,
        of3: Int,
        of7: Int,
        of100: Int,
    ) {
        assertEquals(listOf(of1, of2, of3, of7, of100), listOf(1, 2, 3, 7, 100).map { WinnerPicker.index(seed, it) })
    }

    @Test
    fun `the recorded audit reproduces the winner`() {
        repeat(50) {
            val (winner, audit) = picker.pick(listOf("c@x.com", "a@x.com", "b@x.com"))

            assertEquals(listOf("a@x.com", "b@x.com", "c@x.com"), audit.candidates)
            assertEquals(winner, audit.candidates[WinnerPicker.index(audit.seed, audit.candidates.size)])
            assertEquals(64, audit.seed.length)
        }
    }

    @Test
    fun `the input order does not matter, only the candidates`() {
        val (_, audit) = picker.pick(listOf("b", "a", "a"))

        assertEquals(listOf("a", "b"), audit.candidates)
    }

    @Test
    fun `every draw gets a fresh seed`() {
        assertNotEquals(picker.pick(listOf("a", "b")).audit.seed, picker.pick(listOf("a", "b")).audit.seed)
    }

    @Test
    fun `winners are spread evenly`() {
        val counts = List(3000) { picker.pick(listOf("a", "b", "c")).winner }.groupingBy { it }.eachCount()

        assertEquals(setOf("a", "b", "c"), counts.keys)
        counts.values.forEach { assertTrue(it in 850..1150, "unexpected spread $counts") }
    }

    @Test
    fun `rejects an empty draw`() {
        assertThrows<IllegalArgumentException> { picker.pick(emptyList()) }
        assertThrows<IllegalArgumentException> { WinnerPicker.index("00", 0) }
    }
}
