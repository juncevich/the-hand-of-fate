package com.juncevich.fate.web.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class ErrorHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val errors = ex.bindingResult.fieldErrors.associate { fe: FieldError -> fe.field to fe.defaultMessage }
        log.warn("Validation failed: {}", errors)
        val detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            title = "Validation failed"
            setProperty("timestamp", Instant.now())
            setProperty("errors", errors)
        }
        return ResponseEntity.badRequest().body(detail)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        log.warn("Bad request: {}", ex.message)
        val detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            title = ex.message ?: "Bad request"
            setProperty("timestamp", Instant.now())
        }
        return ResponseEntity.badRequest().body(detail)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ProblemDetail> {
        log.warn("Conflict: {}", ex.message)
        val detail = ProblemDetail.forStatus(HttpStatus.CONFLICT).apply {
            title = ex.message ?: "Conflict"
            setProperty("timestamp", Instant.now())
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ProblemDetail> {
        log.warn("Not found: {}", ex.message)
        val detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND).apply {
            title = ex.message ?: "Not found"
            setProperty("timestamp", Instant.now())
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ProblemDetail> {
        log.error("Unexpected error", ex)
        val detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            title = "Internal server error"
            setProperty("timestamp", Instant.now())
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail)
    }
}
