package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.keycloak.KeycloakProperties
import com.juncevich.fate.auth.internal.port.DeletedAccountRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.auth.internal.service.AccountDeletionService
import com.juncevich.fate.auth.internal.service.AvatarService
import com.juncevich.fate.notification.NotificationOutbox
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.ForbiddenException
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.time.Instant

class AccountDeletionServiceTest {
    private val users = mockk<UserRepositoryPort>()
    private val outbox = mockk<NotificationOutbox>()
    private val avatars = mockk<AvatarService>(relaxed = true)
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)
    private val deletedAccounts = mockk<DeletedAccountRepositoryPort>(relaxed = true)
    private val keycloak =
        KeycloakProperties(
            url = "http://keycloak",
            publicUrl = "http://app/auth",
            clientSecret = "secret",
            reauthenticationMaxAge = Duration.ofMinutes(5)
        )

    private val service =
        AccountDeletionService(
            users,
            outbox,
            avatars,
            events,
            deletedAccounts,
            keycloak
        )

    private fun User.caller(authenticatedAt: Instant? = null) = AuthenticatedUser(id, email, authenticatedAt)

    private fun user() = User(email = "ann@test.com", displayName = "Ann")

    @Test
    fun `a recent sign-in deletes the account, tells other modules and drops queued emails`() {
        val account = user()
        every { users.findById(account.id) } returns account
        every { users.delete(account.id) } just Runs
        every { outbox.cancelFor(account.email) } just Runs
        TransactionSynchronizationManager.initSynchronization()
        try {
            service.deleteAccount(account.caller(authenticatedAt = Instant.now().minusSeconds(60)))

            verify { events.publishEvent(AccountDeleted(account.id, account.email)) }
            verify { outbox.cancelFor(account.email) }
            verify { users.delete(account.id) }
            // Remembered, so a still-valid token cannot recreate it and its Keycloak user goes too.
            verify { deletedAccounts.record(account.id, any()) }
            verify(exactly = 0) { avatars.deleteStoredObject(any()) }

            // The stored avatar goes only once the deletion has committed.
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify { avatars.deleteStoredObject(account.id) }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `an old or unknown sign-in asks to sign in again`() {
        val account = user()
        every { users.findById(account.id) } returns account

        listOf(Instant.now().minus(Duration.ofMinutes(6)), null).forEach { signedInAt ->
            val ex = assertThrows<ForbiddenException> { service.deleteAccount(account.caller(signedInAt)) }
            assertEquals(ErrorCode.REAUTHENTICATION_REQUIRED, ex.code)
        }
        verify(exactly = 0) { users.delete(any()) }
        verify(exactly = 0) { deletedAccounts.record(any(), any()) }
    }
}
