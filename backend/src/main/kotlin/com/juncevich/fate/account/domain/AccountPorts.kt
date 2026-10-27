package com.juncevich.fate.account.domain

import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import java.time.Instant

interface UserRepository {
    fun findById(id: UserId): User?

    fun findByEmail(email: Email): User?

    fun findByTelegramId(telegramId: Long): User?

    fun findAllByEmailIn(emails: Collection<Email>): List<User>

    fun findAllByIdIn(ids: Collection<UserId>): List<User>

    /** Writes a changed copy of an existing account back. */
    fun update(user: User)

    /** Gives the account the id [to]; references follow it. False when [from] does not exist. */
    fun changeId(
        from: UserId,
        to: UserId,
    ): Boolean

    /** Inserts [user] unless an account with its id or email exists; true when it was inserted. */
    fun insertIfAbsent(user: User): Boolean

    fun delete(id: UserId)
}

/** Administration of the accounts held by the authorization server (Keycloak). */
interface IdentityAdmin {
    /** Deletes the account; one that does not exist (any more) counts as deleted. */
    fun deleteUser(userId: UserId)

    /** Ends every session of the account, so no new access tokens are issued for them. */
    fun logoutUser(userId: UserId)
}

/** Accounts deleted here whose identity at the authorization server may still exist. */
interface DeletedAccountRepository {
    fun record(
        userId: UserId,
        deletedAt: Instant,
    )

    fun isDeleted(userId: UserId): Boolean

    /** Oldest first: accounts whose Keycloak user is not confirmed deleted yet. */
    fun findPendingIdentityDeletion(limit: Int): List<UserId>

    fun markIdentityDeleted(
        userId: UserId,
        at: Instant,
    )

    /** Forgets accounts deleted before [before] whose Keycloak user is gone. */
    fun purge(before: Instant): Int
}
