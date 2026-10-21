package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.port.DeletedAccountRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val DISPLAY_NAME_MAX_LENGTH = 100

/** The user a validated Keycloak access token speaks for. [email] is canonical and verified. */
data class KeycloakIdentity(
    val id: UUID,
    val email: String,
    val name: String?,
)

/**
 * Keeps `users` in step with Keycloak, where accounts are created (docs/adr/0001): the first request
 * with a Keycloak token creates the row, its id being the token's subject. A row with the same address
 * but another id (an account from before Keycloak, or one whose Keycloak user was replaced) is taken
 * over instead — Keycloak verified that the caller receives mail there.
 */
@Service
@Transactional
class KeycloakUserProvisioner(
    private val users: UserRepositoryPort,
    private val deletedAccounts: DeletedAccountRepositoryPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** False when the account was deleted: a token issued before that must not bring it back. */
    fun ensureProvisioned(identity: KeycloakIdentity): Boolean {
        if (users.findById(identity.id) != null) return true
        if (deletedAccounts.isDeleted(identity.id)) return false
        val existing = users.findByEmail(identity.email)
        if (existing != null) {
            users.changeId(existing.id, identity.id)
            log.info("Account {} taken over by Keycloak user {}", existing.id, identity.id)
            return true
        }
        users.insertIfAbsent(User(id = identity.id, email = identity.email, displayName = displayName(identity)))
        return true
    }

    private fun displayName(identity: KeycloakIdentity): String =
        (identity.name?.trim()?.takeIf { it.isNotEmpty() } ?: identity.email.substringBefore('@'))
            .take(DISPLAY_NAME_MAX_LENGTH)
}
