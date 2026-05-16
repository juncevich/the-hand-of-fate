package com.juncevich.fate.auth.internal.domain

import com.juncevich.fate.auth.User
import java.time.Instant
import java.util.UUID

class TelegramLinkToken(
    val id: UUID = UUID.randomUUID(),
    val user: User,
    val token: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
) {
    val isExpired get() = Instant.now().isAfter(expiresAt)
}
