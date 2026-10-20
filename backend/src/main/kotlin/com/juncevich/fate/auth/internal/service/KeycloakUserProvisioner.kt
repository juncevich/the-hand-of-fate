package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.port.DeletedAccountRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
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
 * with a Keycloak token creates the row, its id being the token's subject. An account from before
 * Keycloak with the same verified address is taken over instead — Keycloak verified that the caller
 * receives mail there, the same proof a password reset asks for.
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
        if (existing?.emailVerifiedAt != null) {
            users.changeId(existing.id, identity.id)
            log.info("Account {} taken over by Keycloak user {}", existing.id, identity.id)
            return true
        }
        // Never confirmed, so nothing was created with it — and its password may be someone else's.
        existing?.let { users.delete(it.id) }
        users.insertIfAbsent(
            User(
                id = identity.id,
                email = identity.email,
                passwordHash = NO_PASSWORD,
                displayName = displayName(identity),
                emailVerifiedAt = Instant.now()
            )
        )
        return true
    }

    private fun displayName(identity: KeycloakIdentity): String =
        (identity.name?.trim()?.takeIf { it.isNotEmpty() } ?: identity.email.substringBefore('@'))
            .take(DISPLAY_NAME_MAX_LENGTH)

    companion object {
        /**
         * Password hash of accounts created through Keycloak: not a BCrypt hash, so no password
         * matches it. The column goes away with the app's own login (docs/plans/keycloak-auth.md).
         */
        const val NO_PASSWORD = "!keycloak"
    }
}
