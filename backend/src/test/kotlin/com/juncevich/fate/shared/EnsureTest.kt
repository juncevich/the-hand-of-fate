package com.juncevich.fate.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EnsureTest {
    @Test
    fun `a broken rule becomes the client error of its kind`() {
        assertEquals(
            ErrorCode.TITLE_INVALID,
            assertThrows<BadRequestException> { ensureValid(false, ErrorCode.TITLE_INVALID) { "bad" } }.code
        )
        assertEquals(
            ErrorCode.VOTE_NOT_PENDING,
            assertThrows<ConflictException> { ensureState(false, ErrorCode.VOTE_NOT_PENDING) { "busy" } }.code
        )
    }

    @Test
    fun `a checked condition holds afterwards, like after require`() {
        val title: String? = listOf("Vote").firstOrNull()
        ensureValid(title != null, ErrorCode.TITLE_INVALID) { "missing" }
        // Compiles only thanks to the contract: `title` is smart-cast to String.
        assertEquals(4, title.length)

        val round: Any = listOf<Any>(1).first()
        ensureState(round is Int, ErrorCode.VOTE_NOT_PENDING) { "not a round" }
        assertEquals(2, round + 1)
    }
}
