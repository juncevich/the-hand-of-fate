package com.juncevich.fate.auth.internal.port

import com.juncevich.fate.auth.internal.domain.RefreshToken
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepositoryPort {
    /** Locks the row until the transaction ends, so concurrent refreshes of one token serialize. */
    fun findByTokenHash(tokenHash: String): RefreshToken?

    fun save(token: RefreshToken): RefreshToken

    /** Marks a token as rotated at [at]; its row stays so a replay can be detected. */
    fun revoke(
        token: RefreshToken,
        at: Instant,
    )

    fun delete(token: RefreshToken)

    /** Ends a whole login session: every token issued from it, rotated or not. */
    fun deleteFamily(familyId: UUID)

    fun deleteAllByUserId(userId: UUID)

    /** Deletes tokens past their expiry; returns how many were removed. */
    fun deleteExpired(now: Instant): Int
}
