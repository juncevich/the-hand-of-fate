package com.juncevich.fate.auth.internal.port

import com.juncevich.fate.auth.internal.domain.RefreshToken
import java.util.UUID

interface RefreshTokenRepositoryPort {
    fun findByTokenHash(tokenHash: String): RefreshToken?
    fun save(token: RefreshToken): RefreshToken
    fun delete(token: RefreshToken)
    fun deleteAllByUserId(userId: UUID)
}
