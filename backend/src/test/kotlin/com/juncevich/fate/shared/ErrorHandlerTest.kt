package com.juncevich.fate.shared

import com.juncevich.fate.shared.internal.config.ErrorHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException
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
    fun `handleBadRequest - returns 400 with message as title for IllegalArgumentException`() {
        val response = handler.handleBadRequest(IllegalArgumentException("Vote not found"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Vote not found", response.body?.title)
        assertNotNull(response.body?.properties?.get("timestamp"))
    }

    @Test
    fun `handleBadRequest - returns 400 with message as title for BadRequestException`() {
        val response = handler.handleBadRequest(BadRequestException("Invalid email address: nope"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Invalid email address: nope", response.body?.title)
    }

    @Test
    fun `handleConflict - returns 409 for IllegalStateException`() {
        val response = handler.handleConflict(IllegalStateException("Conflict"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("Conflict", response.body?.title)
    }

    @Test
    fun `handleNotFound - returns 404 for NotFoundException`() {
        val response = handler.handleNotFound(NotFoundException("Vote not found"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Vote not found", response.body?.title)
    }

    @Test
    fun `handleConflict - returns 409 for ConflictException`() {
        val response = handler.handleConflict(ConflictException("Vote is already closed"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("Vote is already closed", response.body?.title)
    }

    @Test
    fun `handleForbidden - returns 403`() {
        val response = handler.handleForbidden(ForbiddenException("Only the creator can perform this action"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("Only the creator can perform this action", response.body?.title)
    }

    @Test
    fun `handleOptimisticLocking - returns 409`() {
        val response =
            handler.handleOptimisticLocking(ObjectOptimisticLockingFailureException("Vote", 1L))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("The resource was modified concurrently, please retry", response.body?.title)
    }

    @Test
    fun `handleNotFound - returns 404 for NoSuchElementException`() {
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
