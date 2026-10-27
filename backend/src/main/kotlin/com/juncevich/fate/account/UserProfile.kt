package com.juncevich.fate.account

import com.juncevich.fate.account.domain.User
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId

/** Public identity view for other modules; excludes credentials and mutable account state. */
data class UserProfile(
    val id: UserId,
    val email: Email,
    val displayName: String,
)

fun User.toProfile() = UserProfile(id, email, displayName)
