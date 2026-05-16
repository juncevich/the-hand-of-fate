package com.juncevich.fate.shared

import com.juncevich.fate.shared.internal.config.ErrorHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.BadCredentialsException

class ErrorHandlerTest {
    private val handler = ErrorHandler()

    @Test
    fun `handleAuthentication - returns 401`() {
        val response = handler.handleAuthentication(BadCredentialsException("Invalid credentials"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("Invalid credentials", response.body?.title)
        assertNotNull(response.body?.properties?.get("timestamp"))
    }

    @Test
    fun `handleIllegalArgument - returns 400 with message as title`() {
        val response = handler.handleIllegalArgument(IllegalArgumentException("Vote not found"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Vote not found", response.body?.title)
        assertNotNull(response.body?.properties?.get("timestamp"))
    }

    @Test
    fun `handleIllegalState - returns 409`() {
        val response = handler.handleIllegalState(IllegalStateException("Conflict"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("Conflict", response.body?.title)
    }

    @Test
    fun `handleNotFound - returns 404`() {
        val response = handler.handleNotFound(NoSuchElementException("Not found"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `handleGeneric - returns 500 with generic message`() {
        val response = handler.handleGeneric(RuntimeException("Something broke"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Internal server error", response.body?.title)
    }
}
