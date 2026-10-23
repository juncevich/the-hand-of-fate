package com.juncevich.fate.account.domain

import com.juncevich.fate.account.User
import com.juncevich.fate.shared.uuidV7
import java.time.Instant
import java.util.UUID

class TelegramLinkToken(
    val id: UUID = uuidV7(),
    val user: User,
    val token: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
) {
    val isExpired get() = Instant.now().isAfter(expiresAt)
}
