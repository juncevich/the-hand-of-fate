package com.juncevich.fate.account

import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import java.time.Instant

/** The user a request's access token speaks for; [email] is the token's verified address. */
data class AuthenticatedUser(
    val id: UserId,
    val email: Email,
    /**
     * When the user last proved who they are (the token's `auth_time`), for operations that need
     * a recent sign-in; `null` for tokens that don't say, which then never count as recent.
     */
    val authenticatedAt: Instant? = null,
)
