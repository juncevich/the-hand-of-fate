package com.juncevich.fate.shared

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import java.sql.SQLException

private const val UNIQUE_VIOLATION = "23505"

/**
 * The failure as the client error both transports report — the one place that decides which
 * infrastructure exceptions are the client's business, so REST (`ErrorHandler`) and gRPC
 * (`GrpcErrors`) can't map them differently. `null` means an unexpected failure: a bug, reported
 * as an internal error without its message.
 */
fun Throwable.asClientError(): FateException? =
    when {
        this is FateException -> {
            this
        }

        this is OptimisticLockingFailureException -> {
            ConflictException("The resource was modified concurrently, please retry", ErrorCode.CONCURRENT_MODIFICATION)
        }

        // A unique constraint backs up a rule the code already checks (e.g. a duplicate option title).
        this is DataIntegrityViolationException &&
            causes().filterIsInstance<SQLException>().any { it.sqlState == UNIQUE_VIOLATION } -> {
            ConflictException("The resource already exists", ErrorCode.ALREADY_EXISTS)
        }

        else -> {
            null
        }
    }

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }
