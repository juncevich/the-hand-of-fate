package com.juncevich.fate.account

import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import java.time.Instant
import java.util.UUID

data class AuthenticatedUser(
    val id: UUID,
    val email: String,
    /**
     * When the user last proved who they are (the token's `auth_time`), for operations that need
     * a recent sign-in; `null` for tokens that don't say, which then never count as recent.
     */
    val authenticatedAt: Instant? = null,
)

/** [AuthenticatedUser.id] as the typed id other modules take. */
val AuthenticatedUser.userId: UserId get() = UserId(id)

/** The verified address of the token, in canonical form. */
val AuthenticatedUser.mailbox: Email get() = Email.trusted(email)
