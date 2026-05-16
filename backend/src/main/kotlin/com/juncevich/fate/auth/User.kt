package com.juncevich.fate.auth

import java.time.Instant
import java.util.UUID

class User(
    val id: UUID = UUID.randomUUID(),
    var email: String,
    var passwordHash: String,
    var displayName: String,
    var telegramId: Long? = null,
    var telegramName: String? = null,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
)
