package com.juncevich.fate.auth.internal.web

data class EmailPreferencesDto(
    /** Whether invitations and draw results are emailed to the account's address. */
    val notificationsEnabled: Boolean,
)

data class ProfileResponse(
    val userId: String,
    val email: String,
    val displayName: String,
    /** Versioned URL of the avatar image; `null` when the user has none. */
    val avatarUrl: String?,
)
