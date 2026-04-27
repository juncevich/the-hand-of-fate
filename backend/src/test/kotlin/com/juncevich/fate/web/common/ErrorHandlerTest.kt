package com.juncevich.fate.web.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ErrorHandlerTest {

    private val handler = ErrorHandler()

    @Test
    fun `handleIllegalArgument - returns 400 with message as title`() {
        val response = handler.handleIllegalArgument(IllegalArgumentException("Vote not found"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Vote not found", response.body?.title)
        assertNotNull(response.body?.properties?.get("timestamp"))
    }

    @Test
    fun `handleIllegalArgument - uses fallback title when message is null`() {
        val response = handler.handleIllegalArgument(IllegalArgumentException())

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Bad request", response.body?.title)
    }

    @Test
    fun `handleIllegalState - returns 409 with message as title`() {
        val response = handler.handleIllegalState(IllegalStateException("Only DRAWN votes can be reopened"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("Only DRAWN votes can be reopened", response.body?.title)
        assertNotNull(response.body?.properties?.get("timestamp"))
    }

    @Test
    fun `handleIllegalState - uses fallback title when message is null`() {
        val response = handler.handleIllegalState(IllegalStateException())

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("Conflict", response.body?.title)
    }

    @Test
    fun `handleNotFound - returns 404 with message as title`() {
        val response = handler.handleNotFound(NoSuchElementException("Vote not found"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Vote not found", response.body?.title)
        assertNotNull(response.body?.properties?.get("timestamp"))
    }

    @Test
    fun `handleNotFound - uses fallback title when message is null`() {
        val response = handler.handleNotFound(NoSuchElementException())

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Not found", response.body?.title)
    }

    @Test
    fun `handleGeneric - returns 500 with generic message`() {
        val response = handler.handleGeneric(RuntimeException("database exploded"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Internal server error", response.body?.title)
        assertNotNull(response.body?.properties?.get("timestamp"))
    }

    @Test
    fun `handleGeneric - does not leak exception message to caller`() {
        val response = handler.handleGeneric(RuntimeException("sensitive internal detail"))

        assertEquals("Internal server error", response.body?.title)
    }
}
