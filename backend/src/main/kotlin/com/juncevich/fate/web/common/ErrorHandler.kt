package com.juncevich.fate.web.common

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

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            title = "Validation failed"
            setProperty("timestamp", Instant.now())
            setProperty("errors", ex.bindingResult.fieldErrors.associate { fe: FieldError ->
                fe.field to fe.defaultMessage
            })
        }
        return ResponseEntity.badRequest().body(detail)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        val detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            title = ex.message ?: "Bad request"
            setProperty("timestamp", Instant.now())
        }
        return ResponseEntity.badRequest().body(detail)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ProblemDetail> {
        val detail = ProblemDetail.forStatus(HttpStatus.CONFLICT).apply {
            title = ex.message ?: "Conflict"
            setProperty("timestamp", Instant.now())
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ProblemDetail> {
        val detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND).apply {
            title = ex.message ?: "Not found"
            setProperty("timestamp", Instant.now())
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail)
    }
}
