package com.juncevich.fate.account.domain

import com.juncevich.fate.shared.uuidV7
import java.time.Instant
import java.util.UUID

/** A one-time token that links the Telegram account presenting it to [userId] (5 minutes). */
class TelegramLinkToken(
    val id: UUID = uuidV7(),
    val userId: UUID,
    val token: String,
    val expiresAt: Instant,
    val createdAt: Instant,
) {
    fun isExpiredAt(now: Instant) = now.isAfter(expiresAt)
}

interface TelegramLinkTokenRepository {
    /** Holds the token's row lock until the transaction ends, so a token is consumed once. */
    fun findByToken(token: String): TelegramLinkToken?

    fun insert(token: TelegramLinkToken)

    fun delete(token: TelegramLinkToken)

    fun deleteAllByUserId(userId: UUID)

    /** Deletes tokens past their expiry; returns how many were removed. */
    fun deleteExpired(now: Instant): Int
}
