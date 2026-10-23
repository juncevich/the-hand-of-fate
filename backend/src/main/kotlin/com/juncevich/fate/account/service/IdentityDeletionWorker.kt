package com.juncevich.fate.account.service

import com.juncevich.fate.account.keycloak.KeycloakProperties
import com.juncevich.fate.account.port.DeletedAccountRepositoryPort
import com.juncevich.fate.account.port.IdentityAdminPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

private const val BATCH_SIZE = 20

/**
 * Deletes the Keycloak user of every account deleted here. It runs after the local deletion has
 * committed and retries until Keycloak confirms, so a Keycloak outage cannot leave a deleted
 * account able to sign in for good. Remembered deletions are forgotten after
 * `app.keycloak.deleted-account-retention`.
 */
@Component
class IdentityDeletionWorker(
    private val deletedAccounts: DeletedAccountRepositoryPort,
    private val identityAdmin: IdentityAdminPort,
    private val props: KeycloakProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Whatever fails for one account (Keycloak down, a timeout, a bad answer) must not stop the rest.
    @Suppress("TooGenericExceptionCaught")
    @Scheduled(fixedDelayString = "\${app.keycloak.deletion-delay-ms:30000}")
    fun run() {
        for (userId in deletedAccounts.findPendingIdentityDeletion(BATCH_SIZE)) {
            try {
                identityAdmin.deleteUser(userId)
                deletedAccounts.markIdentityDeleted(userId, Instant.now())
                log.info("Deleted the Keycloak user of account {}", userId)
            } catch (ex: RuntimeException) {
                // Stays pending: retried on the next run.
                log.warn("Could not delete the Keycloak user of account {}: {}", userId, ex.message)
            }
        }
        deletedAccounts.purge(Instant.now().minus(props.deletedAccountRetention))
    }
}
