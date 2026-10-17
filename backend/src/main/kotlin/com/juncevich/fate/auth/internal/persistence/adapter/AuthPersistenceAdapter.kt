package com.juncevich.fate.auth.internal.persistence.adapter

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.domain.RefreshToken
import com.juncevich.fate.auth.internal.domain.TelegramLinkToken
import com.juncevich.fate.auth.internal.persistence.jpa.RefreshTokenJpaRepository
import com.juncevich.fate.auth.internal.persistence.jpa.TelegramLinkTokenJpaRepository
import com.juncevich.fate.auth.internal.persistence.jpa.UserJpaRepository
import com.juncevich.fate.auth.internal.persistence.mapper.toDomain
import com.juncevich.fate.auth.internal.persistence.mapper.toJpaEntity
import com.juncevich.fate.auth.internal.port.RefreshTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.TelegramLinkTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository,
) : UserRepositoryPort {
    override fun findById(id: UUID): User? = userJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByEmail(email: String): User? = userJpaRepository.findByEmail(email)?.toDomain()

    override fun findByTelegramId(telegramId: Long): User? = userJpaRepository.findByTelegramId(telegramId)?.toDomain()

    override fun findAllByEmailIn(emails: Collection<String>): List<User> =
        userJpaRepository.findAllByEmailIn(emails).map { it.toDomain() }

    override fun findAllByIdIn(ids: Collection<UUID>): List<User> =
        userJpaRepository.findAllById(ids).map { it.toDomain() }

    override fun existsByEmail(email: String): Boolean = userJpaRepository.existsByEmail(email)

    override fun save(user: User): User = userJpaRepository.save(user.toJpaEntity()).toDomain()

    override fun delete(id: UUID) {
        userJpaRepository.deleteById(id)
        // The row must be gone before the transaction's other work (and its FK cascades) is checked.
        userJpaRepository.flush()
    }
}

@Component
class RefreshTokenPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository,
    private val refreshTokenJpaRepository: RefreshTokenJpaRepository,
) : RefreshTokenRepositoryPort {
    override fun findByTokenHash(tokenHash: String): RefreshToken? =
        refreshTokenJpaRepository.findByTokenHash(tokenHash)?.toDomain()

    override fun save(token: RefreshToken): RefreshToken {
        val userEntity = userJpaRepository.getReferenceById(token.user.id)
        return refreshTokenJpaRepository.save(token.toJpaEntity(userEntity)).toDomain()
    }

    override fun revoke(
        token: RefreshToken,
        at: Instant,
    ) {
        refreshTokenJpaRepository.revoke(token.id, at)
    }

    override fun delete(token: RefreshToken) {
        refreshTokenJpaRepository.deleteById(token.id)
    }

    override fun deleteFamily(familyId: UUID) {
        refreshTokenJpaRepository.deleteAllByFamilyId(familyId)
    }

    override fun deleteAllByUserId(userId: UUID) {
        refreshTokenJpaRepository.deleteAllByUserId(userId)
    }

    override fun deleteExpired(now: Instant): Int = refreshTokenJpaRepository.deleteExpired(now)
}

@Component
class TelegramLinkTokenPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository,
    private val telegramLinkTokenJpaRepository: TelegramLinkTokenJpaRepository,
) : TelegramLinkTokenRepositoryPort {
    override fun findByToken(token: String): TelegramLinkToken? =
        telegramLinkTokenJpaRepository.findByToken(token)?.toDomain()

    override fun save(token: TelegramLinkToken): TelegramLinkToken {
        val userEntity = userJpaRepository.getReferenceById(token.user.id)
        return telegramLinkTokenJpaRepository.save(token.toJpaEntity(userEntity)).toDomain()
    }

    override fun delete(token: TelegramLinkToken) {
        telegramLinkTokenJpaRepository.deleteById(token.id)
    }

    override fun deleteAllByUserId(userId: UUID) {
        telegramLinkTokenJpaRepository.deleteAllByUserId(userId)
    }

    override fun deleteExpired(now: Instant): Int = telegramLinkTokenJpaRepository.deleteExpired(now)
}
