package com.juncevich.fate.shared

/**
 * Server-side email format guard. The web layer already validates via `@Email`,
 * but service methods are also reached through the gRPC entry point, which does
 * no bean validation — so callers must not assume a well-formed address.
 */
private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

fun isValidEmail(value: String): Boolean = EMAIL_REGEX.matches(value)

fun requireValidEmail(value: String): String {
    if (!isValidEmail(value)) {
        throw BadRequestException("Invalid email address: $value")
    }
    return value
}
