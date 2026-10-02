package com.juncevich.fate.auth

import java.util.UUID

/** Public identity view for other modules; excludes credentials and mutable account state. */
data class UserProfile(
    val id: UUID,
    val email: String,
    val displayName: String,
)

fun User.toProfile() = UserProfile(id, email, displayName)
