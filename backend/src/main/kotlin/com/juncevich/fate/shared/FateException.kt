package com.juncevich.fate.shared

/**
 * Root of the application's expected (client-facing) errors. Every subtype maps to one
 * transport status in `ErrorHandler` (REST) and `GrpcErrors` (gRPC), and its message is
 * shown to the caller — so never put internal details into it.
 *
 * Only these four categories extend the root directly, which keeps status mapping exhaustive;
 * modules may refine a category with their own subclass (e.g. `VoteNotFoundException : NotFoundException`).
 * Anything else thrown — `IllegalStateException`, `NoSuchElementException`, ... — is a bug
 * and is reported as an internal error without its message.
 */
sealed class FateException(
    message: String,
) : RuntimeException(message)

/** 400 / INVALID_ARGUMENT — the request was malformed or failed a business validation rule. */
open class BadRequestException(
    message: String,
) : FateException(message)

/** 404 / NOT_FOUND — the requested resource does not exist. */
open class NotFoundException(
    message: String,
) : FateException(message)

/** 403 / PERMISSION_DENIED — the caller may not perform this action on the resource. */
open class ForbiddenException(
    message: String,
) : FateException(message)

/** 409 / FAILED_PRECONDITION — the resource is in a state that conflicts with the requested action. */
open class ConflictException(
    message: String,
) : FateException(message)

/** Domain counterpart of `require`: throws [BadRequestException] when [condition] is false. */
inline fun ensureValid(
    condition: Boolean,
    message: () -> String,
) {
    if (!condition) throw BadRequestException(message())
}

/**
 * Domain counterpart of `check`: throws [ConflictException] when [condition] is false.
 * Keep `check`/`error` for internal invariants — those are bugs and must surface as 500.
 */
inline fun ensureState(
    condition: Boolean,
    message: () -> String,
) {
    if (!condition) throw ConflictException(message())
}
