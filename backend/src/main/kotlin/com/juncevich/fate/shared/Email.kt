package com.juncevich.fate.shared

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
        /** A user-supplied address: canonicalized and validated ([ErrorCode.INVALID_EMAIL] if malformed). */
        fun of(raw: String) = Email(normalizeEmail(raw))

        /**
         * An address from a trusted source — a stored row, a verified token — canonicalized but not
         * validated, so data written under older rules still loads.
         */
        fun trusted(raw: String) = Email(canonicalEmail(raw))
    }
}
