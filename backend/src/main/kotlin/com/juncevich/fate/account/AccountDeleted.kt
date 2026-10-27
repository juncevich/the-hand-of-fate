package com.juncevich.fate.account

import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId

/**
 * Published inside the transaction that deletes the account, just before the user row goes:
 * other modules remove or anonymize what they keep about [email] and roll back with it on failure.
 * The account's own votes and tokens are removed by the database; its avatar object, after the
 * commit, by `DeletedAccountAvatarCleanup`.
 */
data class AccountDeleted(
    val userId: UserId,
    val email: Email,
)
