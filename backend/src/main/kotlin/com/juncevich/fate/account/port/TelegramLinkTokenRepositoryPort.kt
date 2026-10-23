package com.juncevich.fate.account.port

import com.juncevich.fate.account.domain.TelegramLinkToken
import java.time.Instant
import java.util.UUID

interface TelegramLinkTokenRepositoryPort {
    fun findByToken(token: String): TelegramLinkToken?

    fun save(token: TelegramLinkToken): TelegramLinkToken

    fun delete(token: TelegramLinkToken)

    fun deleteAllByUserId(userId: UUID)

    /** Deletes tokens past their expiry; returns how many were removed. */
    fun deleteExpired(now: Instant): Int
}
