package com.juncevich.fate.shared.internal.config

import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.FateException
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.TooManyRequestsException
import com.juncevich.fate.shared.problem
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.sql.SQLException
import java.time.Instant

/**
 * Maps every REST failure to a `ProblemDetail` whose `title` is the message shown to the user.
 * Extends [ResponseEntityExceptionHandler] so Spring MVC's own exceptions (malformed JSON, path
 * type mismatch, unsupported method, unknown route, ...) keep their 4xx status instead of
 * falling into the generic 500 handler below.
 */
@RestControllerAdvice
class ErrorHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.bindingResult.fieldErrors.associate { fe: FieldError -> fe.field to fe.defaultMessage }
        log.warn("Validation failed: {}", errors)
        val detail =
            problem(HttpStatus.BAD_REQUEST, "Validation failed", ErrorCode.VALIDATION_FAILED).apply {
                setProperty("errors", errors)
                // The violated constraint per field (Email, NotBlank, Size, ...), for clients that translate.
                setProperty("fieldErrors", ex.bindingResult.fieldErrors.associate { it.field to it.code })
            }
        return ResponseEntity.badRequest().headers(headers).body(detail)
    }

    /** Spring MVC exceptions: keep Spring's status and generic detail, align the body with ours. */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        log.warn("{}: {}", statusCode, ex.message)
        if (body is ProblemDetail) {
            body.detail?.let { body.title = it }
            body.setProperty("timestamp", Instant.now())
            body.setProperty("code", springErrorCode(statusCode))
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ResponseEntity<ProblemDetail> {
        log.warn("Unauthorized: {}", ex.message)
        val code = if (ex is BadCredentialsException) ErrorCode.INVALID_CREDENTIALS else ErrorCode.UNAUTHORIZED
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(problem(HttpStatus.UNAUTHORIZED, ex.message ?: "Unauthorized", code))
    }

    @ExceptionHandler(FateException::class)
    fun handleDomain(ex: FateException): ResponseEntity<ProblemDetail> {
        log.warn("{}: {}", ex.httpStatus().reasonPhrase, ex.message)
        return problemResponse(ex.httpStatus(), ex.message ?: ex.httpStatus().reasonPhrase, ex.code)
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLocking(ex: ObjectOptimisticLockingFailureException): ResponseEntity<ProblemDetail> {
        log.warn("Concurrent modification: {}", ex.message)
        return problemResponse(
            HttpStatus.CONFLICT,
            "The resource was modified concurrently, please retry",
            ErrorCode.CONCURRENT_MODIFICATION
        )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException): ResponseEntity<ProblemDetail> {
        val uniqueViolation =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<SQLException>()
                .any { it.sqlState == "23505" }
        return if (uniqueViolation) {
            problemResponse(HttpStatus.CONFLICT, "The resource already exists", ErrorCode.ALREADY_EXISTS)
        } else {
            handleGeneric(ex)
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ProblemDetail> {
        log.error("Unexpected error", ex)
        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", ErrorCode.INTERNAL_ERROR)
    }

    private fun problemResponse(
        status: HttpStatus,
        title: String,
        code: ErrorCode,
    ): ResponseEntity<ProblemDetail> = ResponseEntity.status(status).body(problem(status, title, code))
}

private fun springErrorCode(status: HttpStatusCode): ErrorCode =
    when {
        status.value() == HttpStatus.PAYLOAD_TOO_LARGE.value() -> ErrorCode.PAYLOAD_TOO_LARGE
        status.is5xxServerError -> ErrorCode.INTERNAL_ERROR
        else -> ErrorCode.REQUEST_INVALID
    }

private fun FateException.httpStatus(): HttpStatus =
    when (this) {
        is BadRequestException -> HttpStatus.BAD_REQUEST
        is NotFoundException -> HttpStatus.NOT_FOUND
        is ForbiddenException -> HttpStatus.FORBIDDEN
        is ConflictException -> HttpStatus.CONFLICT
        is TooManyRequestsException -> HttpStatus.TOO_MANY_REQUESTS
    }
