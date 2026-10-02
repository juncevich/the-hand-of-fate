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
    fun `handleDomain - returns 400 with message as title for BadRequestException`() {
        val response = handler.handleDomain(BadRequestException("Invalid email address: nope"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Invalid email address: nope", response.body?.title)
    }

    @Test
    fun `handleDomain - returns 404 for NotFoundException`() {
        val response = handler.handleDomain(NotFoundException("Vote not found"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Vote not found", response.body?.title)
    }

    @Test
    fun `handleDomain - returns 409 for ConflictException`() {
        val response = handler.handleDomain(ConflictException("Vote is already closed"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("Vote is already closed", response.body?.title)
    }

    @Test
    fun `handleDomain - returns 403 for ForbiddenException`() {
        val response = handler.handleDomain(ForbiddenException("Only the creator can perform this action"))

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
    fun `stdlib exceptions are internal errors and do not leak their message`() {
        val response = handler.handleGeneric(IllegalStateException("DrawHistoryJpaEntity 42 is corrupt"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Internal server error", response.body?.title)
    }

    @Test
    fun `handleGeneric - returns 500 with generic message`() {
        val response = handler.handleGeneric(RuntimeException("Something broke"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Internal server error", response.body?.title)
    }

    @Test
    fun `unique constraint violation returns conflict without database details`() {
        val ex = org.springframework.dao.DataIntegrityViolationException("SQL details", java.sql.SQLException("duplicate", "23505"))
        val response = handler.handleDataIntegrity(ex)
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("The resource already exists", response.body?.title)
    }

    @Test
    fun `other integrity violations remain internal errors`() {
        val ex = org.springframework.dao.DataIntegrityViolationException("SQL details", java.sql.SQLException("foreign key", "23503"))
        val response = handler.handleDataIntegrity(ex)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Internal server error", response.body?.title)
    }
}
