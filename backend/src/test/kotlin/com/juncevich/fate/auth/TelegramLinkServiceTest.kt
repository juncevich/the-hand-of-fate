package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.domain.TelegramLinkToken
import com.juncevich.fate.auth.internal.port.TelegramLinkTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class TelegramLinkServiceTest {
    private val linkTokenRepositoryPort = mockk<TelegramLinkTokenRepositoryPort>()
    private val userRepositoryPort = mockk<UserRepositoryPort>()

    private val service = TelegramLinkService(linkTokenRepositoryPort, userRepositoryPort)

    private fun makeUser(id: UUID = UUID.randomUUID()) = User(
        id = id,
        email = "user@test.com",
        passwordHash = "hash",
        displayName = "Test User",
    )

    private fun makeLinkToken(user: User, expired: Boolean = false): TelegramLinkToken {
        val expiresAt = if (expired) Instant.now().minusSeconds(60) else Instant.now().plusSeconds(300)
        return TelegramLinkToken(user = user, token = "token123", expiresAt = expiresAt)
    }

    @Test
    fun `generateLinkToken - revokes existing tokens and saves new one`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId)

        every { linkTokenRepositoryPort.deleteAllByUserId(userId) } just Runs
        every { userRepositoryPort.findById(userId) } returns user
        every { linkTokenRepositoryPort.save(any()) } answers { firstArg() }

        val token = service.generateLinkToken(userId)

        assertNotNull(token)
        assertTrue(token.isNotBlank())
        verify { linkTokenRepositoryPort.deleteAllByUserId(userId) }
        verify { linkTokenRepositoryPort.save(any()) }
    }

    @Test
    fun `generateLinkToken - generated token has no hyphens`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId)

        every { linkTokenRepositoryPort.deleteAllByUserId(userId) } just Runs
        every { userRepositoryPort.findById(userId) } returns user
        every { linkTokenRepositoryPort.save(any()) } answers { firstArg() }

        val token = service.generateLinkToken(userId)

        assertFalse(token.contains("-"))
    }

    @Test
    fun `linkAccount - links telegram to user on valid token`() {
        val user = makeUser()
        val linkToken = makeLinkToken(user)

        every { linkTokenRepositoryPort.findByToken("token123") } returns linkToken
        every { userRepositoryPort.findByTelegramId(42L) } returns null
        every { userRepositoryPort.save(user) } returns user
        every { linkTokenRepositoryPort.delete(linkToken) } just Runs

        val result = service.linkAccount("token123", 42L, "telegram_user")

        assertEquals(42L, result.telegramId)
        assertEquals("telegram_user", result.telegramName)
        verify { userRepositoryPort.save(user) }
        verify { linkTokenRepositoryPort.delete(linkToken) }
    }

    @Test
    fun `linkAccount - throws for unknown token`() {
        every { linkTokenRepositoryPort.findByToken("bad-token") } returns null

        assertThrows<IllegalStateException> {
            service.linkAccount("bad-token", 42L, "user")
        }
    }

    @Test
    fun `linkAccount - throws and deletes expired token`() {
        val user = makeUser()
        val expiredToken = makeLinkToken(user, expired = true)

        every { linkTokenRepositoryPort.findByToken("token123") } returns expiredToken
        every { linkTokenRepositoryPort.delete(expiredToken) } just Runs

        assertThrows<IllegalStateException> {
            service.linkAccount("token123", 42L, "user")
        }
        verify { linkTokenRepositoryPort.delete(expiredToken) }
    }

    @Test
    fun `linkAccount - throws when telegram already linked to another user`() {
        val user = makeUser()
        val otherUser = makeUser()
        val linkToken = makeLinkToken(user)

        every { linkTokenRepositoryPort.findByToken("token123") } returns linkToken
        every { userRepositoryPort.findByTelegramId(42L) } returns otherUser

        assertThrows<IllegalStateException> {
            service.linkAccount("token123", 42L, "user")
        }
    }

    @Test
    fun `unlinkAccount - clears telegram fields`() {
        val user = makeUser().also {
            it.telegramId = 42L
            it.telegramName = "old_name"
        }

        every { userRepositoryPort.findByTelegramId(42L) } returns user
        every { userRepositoryPort.save(user) } returns user

        service.unlinkAccount(42L)

        assertNull(user.telegramId)
        assertNull(user.telegramName)
        verify { userRepositoryPort.save(user) }
    }

    @Test
    fun `unlinkAccount - throws when telegram id not linked`() {
        every { userRepositoryPort.findByTelegramId(99L) } returns null

        assertThrows<IllegalStateException> { service.unlinkAccount(99L) }
    }

    @Test
    fun `unlinkByUserId - clears telegram fields for user`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId).also {
            it.telegramId = 42L
            it.telegramName = "old_name"
        }

        every { userRepositoryPort.findById(userId) } returns user
        every { userRepositoryPort.save(user) } returns user

        service.unlinkByUserId(userId)

        assertNull(user.telegramId)
        assertNull(user.telegramName)
        verify { userRepositoryPort.save(user) }
    }

    @Test
    fun `unlinkByUserId - throws when user has no linked telegram`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId)

        every { userRepositoryPort.findById(userId) } returns user

        assertThrows<IllegalStateException> { service.unlinkByUserId(userId) }
    }

    @Test
    fun `unlinkByUserId - throws when user not found`() {
        val userId = UUID.randomUUID()
        every { userRepositoryPort.findById(userId) } returns null

        assertThrows<NoSuchElementException> { service.unlinkByUserId(userId) }
    }
}
