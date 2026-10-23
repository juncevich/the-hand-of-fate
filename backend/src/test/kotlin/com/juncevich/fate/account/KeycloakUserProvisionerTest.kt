package com.juncevich.fate.account

import com.juncevich.fate.account.domain.DeletedAccountRepository
import com.juncevich.fate.account.domain.UserRepository
import com.juncevich.fate.account.keycloak.KeycloakIdentity
import com.juncevich.fate.account.keycloak.KeycloakUserProvisioner
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class KeycloakUserProvisionerTest {
    private val users = mockk<UserRepository>(relaxed = true)
    private val deletedAccounts = mockk<DeletedAccountRepository>()
    private val provisioner = KeycloakUserProvisioner(users, deletedAccounts)

    private val identity = KeycloakIdentity(UUID.randomUUID(), "kate@test.com", "Kate")

    private fun account() = User(email = identity.email, displayName = "Old")

    private fun inserted(identity: KeycloakIdentity): User {
        every { users.findById(identity.id) } returns null
        every { deletedAccounts.isDeleted(identity.id) } returns false
        every { users.findByEmail(identity.email) } returns null
        val user = slot<User>()
        every { users.insertIfAbsent(capture(user)) } returns true
        assertTrue(provisioner.ensureProvisioned(identity))
        return user.captured
    }

    @Test
    fun `a known account needs nothing`() {
        every { users.findById(identity.id) } returns account()

        assertTrue(provisioner.ensureProvisioned(identity))

        verify(exactly = 0) { users.insertIfAbsent(any()) }
        verify(exactly = 0) { users.changeId(any(), any()) }
    }

    @Test
    fun `a deleted account is not created again`() {
        every { users.findById(identity.id) } returns null
        every { deletedAccounts.isDeleted(identity.id) } returns true

        assertFalse(provisioner.ensureProvisioned(identity))

        verify(exactly = 0) { users.insertIfAbsent(any()) }
    }

    @Test
    fun `an account with the same email is taken over`() {
        val existing = account()
        every { users.findById(identity.id) } returns null
        every { deletedAccounts.isDeleted(identity.id) } returns false
        every { users.findByEmail(identity.email) } returns existing

        assertTrue(provisioner.ensureProvisioned(identity))

        verify { users.changeId(existing.id, identity.id) }
        verify(exactly = 0) { users.insertIfAbsent(any()) }
    }

    @Test
    fun `a new account is named after the token`() {
        val user = inserted(identity)

        assertEquals(identity.id, user.id)
        assertEquals("kate@test.com", user.email)
        assertEquals("Kate", user.displayName)
    }

    @Test
    fun `without a usable name the account is named after the email, at most 100 characters`() {
        assertEquals("kate", inserted(identity.copy(name = null)).displayName)
        assertEquals("kate", inserted(identity.copy(name = "   ")).displayName)
        assertEquals("K".repeat(100), inserted(identity.copy(name = " ${"K".repeat(150)} ")).displayName)
    }
}
