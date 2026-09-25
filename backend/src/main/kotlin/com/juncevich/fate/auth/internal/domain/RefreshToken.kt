package com.juncevich.fate.auth.internal.domain

import com.juncevich.fate.auth.User
import com.juncevich.fate.shared.uuidV7
import java.time.Instant
import java.util.UUID

class RefreshToken(
    val id: UUID = uuidV7(),
    val user: User,
    val tokenHash: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
) {
    val isExpired get() = Instant.now().isAfter(expiresAt)
}
