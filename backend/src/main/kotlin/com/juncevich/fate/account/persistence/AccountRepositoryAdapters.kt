package com.juncevich.fate.account.persistence

import com.juncevich.fate.account.domain.DeletedAccountRepository
import com.juncevich.fate.account.domain.TelegramLinkToken
import com.juncevich.fate.account.domain.TelegramLinkTokenRepository
import com.juncevich.fate.account.domain.User
import com.juncevich.fate.account.domain.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// New rows go through EntityManager.persist: with an assigned id, JpaRepository.save() would
// merge and query the row first.

// Transactional like Spring Data's own repositories: inside a service transaction it joins it; called
// without one (AvatarService keeps storage I/O out of transactions) it still loads, writes and flushes.
@Component
@Transactional
class UserRepositoryAdapter(
    private val users: UserJpaRepository,
) : UserRepository {
    override fun findById(id: UUID): User? = users.findById(id).orElse(null)?.toDomain()

    override fun findByEmail(email: String): User? = users.findByEmail(email)?.toDomain()

    override fun findByTelegramId(telegramId: Long): User? = users.findByTelegramId(telegramId)?.toDomain()

    override fun findAllByEmailIn(emails: Collection<String>): List<User> =
        users.findAllByEmailIn(emails).map {
            it.toDomain()
        }

    override fun findAllByIdIn(ids: Collection<UUID>): List<User> = users.findAllById(ids).map { it.toDomain() }

    /** Writes onto the managed row (loaded earlier in the transaction, so no extra query); dirty checking does the rest. */
    override fun update(user: User) {
        val entity = users.findById(user.id).orElseThrow { IllegalStateException("User ${user.id} not found") }
        entity.email = user.email
        entity.displayName = user.displayName
        entity.telegramId = user.telegramId
        entity.telegramName = user.telegramName
        entity.avatarUpdatedAt = user.avatarUpdatedAt
    }

    override fun changeId(
        from: UUID,
        to: UUID,
    ): Boolean = users.changeId(from, to) > 0

    override fun insertIfAbsent(user: User): Boolean =
        users.insertIfAbsent(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            createdAt = user.createdAt
        ) >
            0

    override fun delete(id: UUID) {
        users.deleteById(id)
        // The row must be gone before the transaction's other work (and its FK cascades) is checked.
        users.flush()
    }
}

@Component
@Transactional
class TelegramLinkTokenRepositoryAdapter(
    private val tokens: TelegramLinkTokenJpaRepository,
    private val em: EntityManager,
) : TelegramLinkTokenRepository {
    override fun findByToken(token: String): TelegramLinkToken? = tokens.findByToken(token)?.toDomain()

    override fun insert(token: TelegramLinkToken) =
        em.persist(TelegramLinkTokenJpaEntity(token.id, token.userId, token.token, token.expiresAt, token.createdAt))

    override fun delete(token: TelegramLinkToken) = tokens.deleteById(token.id)

    override fun deleteAllByUserId(userId: UUID) = tokens.deleteAllByUserId(userId)

    override fun deleteExpired(now: Instant): Int = tokens.deleteExpired(now)
}

@Component
@Transactional
class DeletedAccountRepositoryAdapter(
    private val deletedAccounts: DeletedAccountJpaRepository,
    private val em: EntityManager,
) : DeletedAccountRepository {
    override fun record(
        userId: UUID,
        deletedAt: Instant,
    ) = em.persist(DeletedAccountJpaEntity(userId, deletedAt))

    override fun isDeleted(userId: UUID): Boolean = deletedAccounts.existsById(userId)

    override fun findPendingIdentityDeletion(limit: Int): List<UUID> =
        deletedAccounts.findPendingIdentityDeletion(Limit.of(limit))

    override fun markIdentityDeleted(
        userId: UUID,
        at: Instant,
    ) = deletedAccounts.markIdentityDeleted(userId, at)

    override fun purge(before: Instant): Int = deletedAccounts.purge(before)
}

private fun UserJpaEntity.toDomain() =
    User(
        id = id,
        email = email,
        displayName = displayName,
        telegramId = telegramId,
        telegramName = telegramName,
        avatarUpdatedAt = avatarUpdatedAt,
        createdAt = createdAt
    )

private fun TelegramLinkTokenJpaEntity.toDomain() =
    TelegramLinkToken(id = id, userId = userId, token = token, expiresAt = expiresAt, createdAt = createdAt)
