package com.juncevich.fate.account.service

import com.juncevich.fate.account.port.IdentityAdminPort
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SessionService(
    private val identityAdmin: IdentityAdminPort,
) {
    /** Ends the user's sessions in Keycloak; access tokens already issued live until they expire. */
    fun logoutEverywhere(userId: UUID) = identityAdmin.logoutUser(userId)
}
