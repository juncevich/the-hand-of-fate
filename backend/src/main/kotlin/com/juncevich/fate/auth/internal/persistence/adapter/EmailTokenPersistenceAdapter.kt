package com.juncevich.fate.auth.internal.persistence.adapter

import com.juncevich.fate.auth.internal.domain.EmailToken
import com.juncevich.fate.auth.internal.domain.EmailTokenPurpose
import com.juncevich.fate.auth.internal.persistence.entity.EmailTokenJpaEntity
import com.juncevich.fate.auth.internal.persistence.jpa.EmailTokenJpaRepository
import com.juncevich.fate.auth.internal.persistence.jpa.UserJpaRepository
import com.juncevich.fate.auth.internal.persistence.mapper.toDomain
import com.juncevich.fate.auth.internal.port.EmailTokenRepositoryPort
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class EmailTokenPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository,
    private val emailTokenJpaRepository: EmailTokenJpaRepository,
) : EmailTokenRepositoryPort {
    override fun findByTokenHash(
        tokenHash: String,
        purpose: EmailTokenPurpose,
    ): EmailToken? = emailTokenJpaRepository.findByTokenHashAndPurpose(tokenHash, purpose)?.toDomain()

    override fun findByUser(
        userId: UUID,
        purpose: EmailTokenPurpose,
    ): EmailToken? = emailTokenJpaRepository.findByUserIdAndPurpose(userId, purpose)?.toDomain()

    override fun replace(token: EmailToken) {
        emailTokenJpaRepository.deleteByUserIdAndPurpose(token.user.id, token.purpose)
        emailTokenJpaRepository.save(
            EmailTokenJpaEntity(
                id = token.id,
                user = userJpaRepository.getReferenceById(token.user.id),
                purpose = token.purpose,
                tokenHash = token.tokenHash,
                expiresAt = token.expiresAt,
                createdAt = token.createdAt
            )
        )
    }

    override fun delete(token: EmailToken) {
        emailTokenJpaRepository.deleteById(token.id)
    }

    override fun deleteExpired(now: Instant): Int = emailTokenJpaRepository.deleteExpired(now)

    private fun EmailTokenJpaEntity.toDomain() =
        EmailToken(
            id = id,
            user = user.toDomain(),
            purpose = purpose,
            tokenHash = tokenHash,
            expiresAt = expiresAt,
            createdAt = createdAt
        )
}
