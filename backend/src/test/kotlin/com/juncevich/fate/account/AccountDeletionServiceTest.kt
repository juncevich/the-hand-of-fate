package com.juncevich.fate.account

import com.juncevich.fate.FIXED_CLOCK
import com.juncevich.fate.NOW
import com.juncevich.fate.account.deletion.AccountDeletionService
import com.juncevich.fate.account.domain.DeletedAccountRepository
import com.juncevich.fate.account.domain.User
import com.juncevich.fate.account.domain.UserRepository
import com.juncevich.fate.account.keycloak.KeycloakProperties
import com.juncevich.fate.notification.NotificationOutbox
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.UserId
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AccountDeletionServiceTest {
    private val users = mockk<UserRepository>()
    private val outbox = mockk<NotificationOutbox>()
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)
    private val deletedAccounts = mockk<DeletedAccountRepository>(relaxed = true)
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
            events,
            deletedAccounts,
            keycloak,
            FIXED_CLOCK
        )

    private fun User.caller(authenticatedAt: Instant? = null) = AuthenticatedUser(id, email, authenticatedAt)

    private fun user() = User(id = UserId(UUID.randomUUID()), email = Email.of("ann@test.com"), displayName = "Ann", createdAt = NOW)

    @Test
    fun `a recent sign-in deletes the account, tells other modules and drops queued emails`() {
        val account = user()
        every { users.findById(account.id) } returns account
        every { users.delete(account.id) } just Runs
        every { outbox.cancelFor(account.email) } just Runs
        service.deleteAccount(account.caller(authenticatedAt = NOW.minusSeconds(60)))

        // Listeners also remove the avatar object, after the commit (DeletedAccountAvatarCleanup).
        verify { events.publishEvent(AccountDeleted(account.id, account.email)) }
        verify { outbox.cancelFor(account.email) }
        verify { users.delete(account.id) }
        // Remembered, so a still-valid token cannot recreate it and its Keycloak user goes too.
        verify { deletedAccounts.record(account.id, NOW) }
    }

    @Test
    fun `an old or unknown sign-in asks to sign in again`() {
        val account = user()
        every { users.findById(account.id) } returns account

        listOf(NOW.minus(Duration.ofMinutes(6)), null).forEach { signedInAt ->
            val ex = assertThrows<ForbiddenException> { service.deleteAccount(account.caller(signedInAt)) }
            assertEquals(ErrorCode.REAUTHENTICATION_REQUIRED, ex.code)
        }
        verify(exactly = 0) { users.delete(any()) }
        verify(exactly = 0) { deletedAccounts.record(any(), any()) }
    }
}
