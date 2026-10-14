package com.juncevich.fate.auth.internal.service

import java.util.UUID

/** A freshly started session: the token pair and the profile of the user it belongs to. */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val userId: UUID,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
)
