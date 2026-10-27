package com.juncevich.fate.account.persistence

import com.juncevich.fate.account.domain.DeletedAccountRepository
import com.juncevich.fate.account.domain.TelegramLinkToken
import com.juncevich.fate.account.domain.TelegramLinkTokenRepository
import com.juncevich.fate.account.domain.User
import com.juncevich.fate.account.domain.UserRepository
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// New rows go through EntityManager.persist: with an assigned id, JpaRepository.save() would
// merge and query the row first.

// Transactional like Spring Data's own repositories: inside a service transaction it joins it; called
// without one (AvatarService keeps storage I/O out of transactions) it still loads, writes and flushes.
@Component
@Transactional
class UserRepositoryAdapter(
    private val users: UserJpaRepository,
) : UserRepository {
    override fun findById(id: UserId): User? = users.findById(id.value).orElse(null)?.toDomain()

    override fun findByEmail(email: Email): User? = users.findByEmail(email.value)?.toDomain()

    override fun findByTelegramId(telegramId: Long): User? = users.findByTelegramId(telegramId)?.toDomain()

    override fun findAllByEmailIn(emails: Collection<Email>): List<User> =
        users.findAllByEmailIn(emails.map { it.value }).map { it.toDomain() }

    override fun findAllByIdIn(ids: Collection<UserId>): List<User> =
        users.findAllById(ids.map { it.value }).map { it.toDomain() }

    /** Writes onto the managed row (loaded earlier in the transaction, so no extra query); dirty checking does the rest. */
    override fun update(user: User) {
        val entity = users.findById(user.id.value).orElseThrow { IllegalStateException("User ${user.id} not found") }
        entity.email = user.email.value
        entity.displayName = user.displayName
        entity.telegramId = user.telegramId
        entity.telegramName = user.telegramName
        entity.avatarUpdatedAt = user.avatarUpdatedAt
    }

    override fun changeId(
        from: UserId,
        to: UserId,
    ): Boolean = users.changeId(from.value, to.value) > 0

    override fun insertIfAbsent(user: User): Boolean =
        users.insertIfAbsent(
            id = user.id.value,
            email = user.email.value,
            displayName = user.displayName,
            createdAt = user.createdAt
        ) > 0

    override fun delete(id: UserId) {
        users.deleteById(id.value)
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
        em.persist(
            TelegramLinkTokenJpaEntity(token.id, token.userId.value, token.token, token.expiresAt, token.createdAt)
        )

    override fun delete(token: TelegramLinkToken) = tokens.deleteById(token.id)

    override fun deleteAllByUserId(userId: UserId) = tokens.deleteAllByUserId(userId.value)

    override fun deleteExpired(now: Instant): Int = tokens.deleteExpired(now)
}

@Component
@Transactional
class DeletedAccountRepositoryAdapter(
    private val deletedAccounts: DeletedAccountJpaRepository,
    private val em: EntityManager,
) : DeletedAccountRepository {
    override fun record(
        userId: UserId,
        deletedAt: Instant,
    ) = em.persist(DeletedAccountJpaEntity(userId.value, deletedAt))

    override fun isDeleted(userId: UserId): Boolean = deletedAccounts.existsById(userId.value)

    override fun findPendingIdentityDeletion(limit: Int): List<UserId> =
        deletedAccounts.findPendingIdentityDeletion(Limit.of(limit)).map(::UserId)

    override fun markIdentityDeleted(
        userId: UserId,
        at: Instant,
    ) = deletedAccounts.markIdentityDeleted(userId.value, at)

    override fun purge(before: Instant): Int = deletedAccounts.purge(before)
}

private fun UserJpaEntity.toDomain() =
    User(
        id = UserId(id),
        email = Email.trusted(email),
        displayName = displayName,
        telegramId = telegramId,
        telegramName = telegramName,
        avatarUpdatedAt = avatarUpdatedAt,
        createdAt = createdAt
    )

private fun TelegramLinkTokenJpaEntity.toDomain() =
    TelegramLinkToken(id = id, userId = UserId(userId), token = token, expiresAt = expiresAt, createdAt = createdAt)
