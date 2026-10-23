package com.juncevich.fate.account

import com.juncevich.fate.account.domain.User
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import java.util.UUID

/** Public identity view for other modules; excludes credentials and mutable account state. */
data class UserProfile(
    val id: UUID,
    val email: String,
    val displayName: String,
)

fun User.toProfile() = UserProfile(id, email, displayName)

/** [UserProfile.id] as the typed id other modules take. */
val UserProfile.userId: UserId get() = UserId(id)

/** [UserProfile.email] — stored, so already canonical. */
val UserProfile.mailbox: Email get() = Email.trusted(email)
