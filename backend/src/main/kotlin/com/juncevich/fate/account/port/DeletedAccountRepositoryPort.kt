package com.juncevich.fate.account.port

import java.time.Instant
import java.util.UUID

/** Accounts deleted here whose identity at the authorization server may still exist. */
interface DeletedAccountRepositoryPort {
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
