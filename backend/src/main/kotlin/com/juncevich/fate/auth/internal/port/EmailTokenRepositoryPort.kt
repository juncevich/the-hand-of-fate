package com.juncevich.fate.auth.internal.port

import com.juncevich.fate.auth.internal.domain.EmailToken
import com.juncevich.fate.auth.internal.domain.EmailTokenPurpose
import java.time.Instant
import java.util.UUID

interface EmailTokenRepositoryPort {
    /** Locks the row until the transaction ends, so a token can be consumed only once. */
    fun findByTokenHash(
        tokenHash: String,
        purpose: EmailTokenPurpose,
    ): EmailToken?

    fun findByUser(
        userId: UUID,
        purpose: EmailTokenPurpose,
    ): EmailToken?

    /** Replaces the user's token for [EmailToken.purpose]: there is at most one per purpose. */
    fun replace(token: EmailToken)

    fun delete(token: EmailToken)

    fun deleteExpired(now: Instant): Int
}
