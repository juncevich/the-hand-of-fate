package com.juncevich.fate.auth

import java.util.UUID

data class AuthenticatedUser(
    val id: UUID,
    val email: String,
)
