package com.juncevich.fate.auth.internal.domain

import com.juncevich.fate.auth.User
import com.juncevich.fate.shared.uuidV7
import java.time.Instant
import java.util.UUID

/**
 * One link of a login session's rotation chain. All tokens issued from one login share [familyId];
 * a rotated token keeps its row with [revokedAt] set so that a later replay can be recognized.
 */
class RefreshToken(
    val id: UUID = uuidV7(),
    val user: User,
    val tokenHash: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
    val familyId: UUID = id,
    val revokedAt: Instant? = null,
) {
    val isExpired get() = Instant.now().isAfter(expiresAt)
}
