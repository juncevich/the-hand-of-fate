package com.juncevich.fate.vote.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PagingTest {
    @Test
    fun `a page query has a non-negative number and a positive size`() {
        assertThrows<IllegalArgumentException> { PageQuery(-1, 10) }
        assertThrows<IllegalArgumentException> { PageQuery(0, 0) }
    }

    @Test
    fun `mapping a page keeps its query and total`() {
        val page = PageResult(listOf(1, 2), PageQuery(3, 2), 8).map { it * 10 }
        assertEquals(PageResult(listOf(10, 20), PageQuery(3, 2), 8), page)
    }
}
