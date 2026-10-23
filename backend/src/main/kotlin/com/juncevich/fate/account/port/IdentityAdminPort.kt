package com.juncevich.fate.account.port

import java.util.UUID

/** Administration of the accounts held by the authorization server (Keycloak). */
interface IdentityAdminPort {
    /** Deletes the account; one that does not exist (any more) counts as deleted. */
    fun deleteUser(userId: UUID)

    /** Ends every session of the account, so no new access tokens are issued for them. */
    fun logoutUser(userId: UUID)
}
