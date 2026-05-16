package com.juncevich.fate.auth.internal.persistence.adapter

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.domain.RefreshToken
import com.juncevich.fate.auth.internal.domain.TelegramLinkToken
import com.juncevich.fate.auth.internal.persistence.entity.RefreshTokenJpaEntity
import com.juncevich.fate.auth.internal.persistence.entity.TelegramLinkTokenJpaEntity
import com.juncevich.fate.auth.internal.persistence.jpa.RefreshTokenJpaRepository
import com.juncevich.fate.auth.internal.persistence.jpa.TelegramLinkTokenJpaRepository
import com.juncevich.fate.auth.internal.persistence.jpa.UserJpaRepository
import com.juncevich.fate.auth.internal.persistence.mapper.toDomain
import com.juncevich.fate.auth.internal.persistence.mapper.toJpaEntity
import com.juncevich.fate.auth.internal.port.RefreshTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.TelegramLinkTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository,
) : UserRepositoryPort {

    override fun findById(id: UUID): User? =
        userJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByEmail(email: String): User? =
        userJpaRepository.findByEmail(email)?.toDomain()

    override fun findByTelegramId(telegramId: Long): User? =
        userJpaRepository.findByTelegramId(telegramId)?.toDomain()

    override fun findAllByEmailIn(emails: Collection<String>): List<User> =
        userJpaRepository.findAllByEmailIn(emails).map { it.toDomain() }

    override fun existsByEmail(email: String): Boolean =
        userJpaRepository.existsByEmail(email)

    override fun save(user: User): User =
        userJpaRepository.save(user.toJpaEntity()).toDomain()
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
        return refreshTokenJpaRepository.save(
            RefreshTokenJpaEntity(
                id = token.id,
                user = userEntity,
                tokenHash = token.tokenHash,
                expiresAt = token.expiresAt,
                createdAt = token.createdAt,
            )
        ).toDomain()
    }

    override fun delete(token: RefreshToken) {
        refreshTokenJpaRepository.deleteById(token.id)
    }

    override fun deleteAllByUserId(userId: UUID) {
        refreshTokenJpaRepository.deleteAllByUserId(userId)
    }
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
        return telegramLinkTokenJpaRepository.save(
            TelegramLinkTokenJpaEntity(
                id = token.id,
                user = userEntity,
                token = token.token,
                expiresAt = token.expiresAt,
                createdAt = token.createdAt,
            )
        ).toDomain()
    }

    override fun delete(token: TelegramLinkToken) {
        telegramLinkTokenJpaRepository.deleteById(token.id)
    }

    override fun deleteAllByUserId(userId: UUID) {
        telegramLinkTokenJpaRepository.deleteAllByUserId(userId)
    }
}
