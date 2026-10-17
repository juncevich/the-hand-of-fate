package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.config.RateLimitProperties
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.auth.internal.service.AccountDeletionService
import com.juncevich.fate.auth.internal.service.AvatarService
import com.juncevich.fate.auth.internal.service.LoginThrottle
import com.juncevich.fate.auth.internal.service.PasswordConfirmation
import com.juncevich.fate.notification.NotificationOutbox
import com.juncevich.fate.shared.BadRequestException
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

class AccountDeletionServiceTest {
    private val users = mockk<UserRepositoryPort>()
    private val outbox = mockk<NotificationOutbox>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val loginThrottle = LoginThrottle(RateLimitProperties(loginFailuresPerAccount = 2))
    private val avatars = mockk<AvatarService>(relaxed = true)
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service =
        AccountDeletionService(
            users,
            outbox,
            PasswordConfirmation(passwordEncoder, loginThrottle),
            loginThrottle,
            avatars,
            events
        )

    private fun user() = User(email = "ann@test.com", passwordHash = "hash:old-password", displayName = "Ann", emailVerifiedAt = Instant.now())

    @Test
    fun `deleting the account needs the password, tells other modules and drops queued emails`() {
        val account = user()
        every { users.findById(account.id) } returns account
        every { passwordEncoder.matches("old-password", "hash:old-password") } returns true
        every { users.delete(account.id) } just Runs
        every { outbox.cancelFor(account.email) } just Runs
        TransactionSynchronizationManager.initSynchronization()
        try {
            service.deleteAccount(account.id, "old-password")

            verify { events.publishEvent(AccountDeleted(account.id, account.email)) }
            verify { outbox.cancelFor(account.email) }
            verify { users.delete(account.id) }
            verify(exactly = 0) { avatars.deleteStoredObject(any()) }

            // The stored avatar goes only once the deletion has committed.
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify { avatars.deleteStoredObject(account.id) }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `a wrong password deletes nothing`() {
        val account = user()
        every { users.findById(account.id) } returns account
        every { passwordEncoder.matches(any(), any()) } returns false

        val ex = assertThrows<BadRequestException> { service.deleteAccount(account.id, "guess") }

        assertEquals("WRONG_PASSWORD", ex.code)
        verify(exactly = 0) { users.delete(any()) }
        verify(exactly = 0) { events.publishEvent(any<Any>()) }
    }
}
