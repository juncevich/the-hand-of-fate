package com.juncevich.fate.account.keycloak

import com.juncevich.fate.account.domain.IdentityAdmin
import com.juncevich.fate.shared.UserId
import org.springframework.stereotype.Service

@Service
class SessionService(
    private val identityAdmin: IdentityAdmin,
) {
    /** Ends the user's sessions in Keycloak; access tokens already issued live until they expire. */
    fun logoutEverywhere(userId: UserId) = identityAdmin.logoutUser(userId)
}
