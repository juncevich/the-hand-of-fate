package com.juncevich.fate.shared.internal.config

import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.core.AuthenticationException
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
        val detail =
            ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
                title = "Validation failed"
                setProperty("timestamp", Instant.now())
                setProperty("errors", errors)
            }
        return ResponseEntity.badRequest().body(detail)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ResponseEntity<ProblemDetail> {
        log.warn("Unauthorized: {}", ex.message)
        val detail =
            ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED).apply {
                title = ex.message ?: "Unauthorized"
                setProperty("timestamp", Instant.now())
            }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(detail)
    }

    @ExceptionHandler(IllegalArgumentException::class, BadRequestException::class)
    fun handleBadRequest(ex: Exception): ResponseEntity<ProblemDetail> = problemResponse(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(IllegalStateException::class, ConflictException::class)
    fun handleConflict(ex: Exception): ResponseEntity<ProblemDetail> = problemResponse(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(NotFoundException::class, NoSuchElementException::class)
    fun handleNotFound(ex: Exception): ResponseEntity<ProblemDetail> = problemResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ProblemDetail> =
        problemResponse(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLocking(ex: ObjectOptimisticLockingFailureException): ResponseEntity<ProblemDetail> {
        log.warn("Concurrent modification: {}", ex.message)
        return problemResponse(HttpStatus.CONFLICT, "The resource was modified concurrently, please retry")
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ProblemDetail> {
        log.error("Unexpected error", ex)
        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
    }

    private fun problemResponse(
        status: HttpStatus,
        ex: Exception,
    ): ResponseEntity<ProblemDetail> {
        log.warn("{}: {}", status.reasonPhrase, ex.message)
        return problemResponse(status, ex.message ?: status.reasonPhrase)
    }

    private fun problemResponse(
        status: HttpStatus,
        title: String,
    ): ResponseEntity<ProblemDetail> {
        val detail =
            ProblemDetail.forStatus(status).apply {
                this.title = title
                setProperty("timestamp", Instant.now())
            }
        return ResponseEntity.status(status).body(detail)
    }
}
