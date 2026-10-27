package com.juncevich.fate.shared

private const val MAX_EMAIL_LENGTH = 255
private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/**
 * An email address in canonical form (trimmed, lower-case) — the form addresses are stored and
 * compared in, so two [Email]s are equal exactly when they name the same mailbox.
 */
@JvmInline
value class Email private constructor(
    val value: String,
) {
    override fun toString() = value

    companion object {
        /**
         * A user-supplied address: canonicalized and validated ([ErrorCode.INVALID_EMAIL] if malformed).
         * The web layer validates with `@Email` too, but the gRPC entry point does no bean validation.
         */
        fun of(raw: String): Email {
            val canonical = canonical(raw)
            if (canonical.length > MAX_EMAIL_LENGTH || !EMAIL_REGEX.matches(canonical)) {
                throw BadRequestException("Invalid email address: $canonical", ErrorCode.INVALID_EMAIL)
            }
            return Email(canonical)
        }

        /**
         * An address from a trusted source — a stored row, a verified token — canonicalized but not
         * validated, so data written under older rules still loads.
         */
        fun trusted(raw: String) = Email(canonical(raw))

        private fun canonical(raw: String) = raw.trim().lowercase()
    }
}
