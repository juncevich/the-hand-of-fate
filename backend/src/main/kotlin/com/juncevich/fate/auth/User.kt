package com.juncevich.fate.auth

import com.juncevich.fate.shared.uuidV7
import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID = uuidV7(),
    var email: String,
    var passwordHash: String,
    var displayName: String,
    var telegramId: Long? = null,
    var telegramName: String? = null,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
)
