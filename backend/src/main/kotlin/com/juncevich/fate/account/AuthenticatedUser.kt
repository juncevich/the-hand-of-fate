package com.juncevich.fate.account

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
