package com.juncevich.fate.account.domain

import com.juncevich.fate.account.domain.User
import java.time.Instant
import java.util.UUID

interface UserRepository {
    fun findById(id: UUID): User?

    fun findByEmail(email: String): User?

    fun findByTelegramId(telegramId: Long): User?

    fun findAllByEmailIn(emails: Collection<String>): List<User>

    fun findAllByIdIn(ids: Collection<UUID>): List<User>

    /** Writes a changed copy of an existing account back. */
    fun update(user: User)

    /** Gives the account the id [to]; references follow it. False when [from] does not exist. */
    fun changeId(
        from: UUID,
        to: UUID,
    ): Boolean

    /** Inserts [user] unless an account with its id or email exists; true when it was inserted. */
    fun insertIfAbsent(user: User): Boolean

    fun delete(id: UUID)
}

/** Administration of the accounts held by the authorization server (Keycloak). */
interface IdentityAdmin {
    /** Deletes the account; one that does not exist (any more) counts as deleted. */
    fun deleteUser(userId: UUID)

    /** Ends every session of the account, so no new access tokens are issued for them. */
    fun logoutUser(userId: UUID)
}

/** Accounts deleted here whose identity at the authorization server may still exist. */
interface DeletedAccountRepository {
    fun record(
        userId: UUID,
        deletedAt: Instant,
    )

    fun isDeleted(userId: UUID): Boolean

    /** Oldest first: accounts whose Keycloak user is not confirmed deleted yet. */
    fun findPendingIdentityDeletion(limit: Int): List<UUID>

    fun markIdentityDeleted(
        userId: UUID,
        at: Instant,
    )

    /** Forgets accounts deleted before [before] whose Keycloak user is gone. */
    fun purge(before: Instant): Int
}
