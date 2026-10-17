package com.juncevich.fate.auth

import java.util.UUID

/**
 * Published inside the transaction that deletes the account, just before the user row goes:
 * other modules remove or anonymize what they keep about [email] and roll back with it on failure.
 * The account's own votes, tokens and avatar are removed by the auth module and the database.
 */
data class AccountDeleted(
    val userId: UUID,
    val email: String,
)
