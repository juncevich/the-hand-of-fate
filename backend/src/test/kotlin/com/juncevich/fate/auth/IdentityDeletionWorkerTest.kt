package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.keycloak.KeycloakProperties
import com.juncevich.fate.auth.internal.port.DeletedAccountRepositoryPort
import com.juncevich.fate.auth.internal.port.IdentityAdminPort
import com.juncevich.fate.auth.internal.service.IdentityDeletionWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class IdentityDeletionWorkerTest {
    private val deletedAccounts = mockk<DeletedAccountRepositoryPort>(relaxed = true)
    private val identityAdmin = mockk<IdentityAdminPort>()
    private val props =
        KeycloakProperties(
            url = "http://keycloak",
            publicUrl = "http://app/auth",
            clientSecret = "secret",
            deletedAccountRetention = Duration.ofDays(1)
        )
    private val worker = IdentityDeletionWorker(deletedAccounts, identityAdmin, props)

    @Test
    fun `deletes pending Keycloak users, keeps failed ones pending and purges old deletions`() {
        val done = UUID.randomUUID()
        val failing = UUID.randomUUID()
        every { deletedAccounts.findPendingIdentityDeletion(any()) } returns listOf(failing, done)
        every { identityAdmin.deleteUser(failing) } throws IllegalStateException("Keycloak is down")
        every { identityAdmin.deleteUser(done) } returns Unit

        worker.run()

        verify { deletedAccounts.markIdentityDeleted(done, any()) }
        verify(exactly = 0) { deletedAccounts.markIdentityDeleted(failing, any()) }
        verify {
            deletedAccounts.purge(
                match { it.isBefore(Instant.now().minus(Duration.ofHours(23))) && it.isAfter(Instant.now().minus(Duration.ofHours(25))) }
            )
        }
    }
}
