package com.juncevich.fate.service

import com.juncevich.fate.domain.auth.TelegramLinkToken
import com.juncevich.fate.domain.auth.TelegramLinkTokenRepository
import com.juncevich.fate.domain.user.User
import com.juncevich.fate.domain.user.UserRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Optional
import java.util.UUID

class TelegramLinkServiceTest {

    private val linkTokenRepository = mockk<TelegramLinkTokenRepository>()
    private val userRepository = mockk<UserRepository>()

    private val service = TelegramLinkService(linkTokenRepository, userRepository)

    private fun makeUser(id: UUID = UUID.randomUUID()) = User(
        email = "user@test.com",
        passwordHash = "hash",
        displayName = "Test User",
    ).also { u ->
        val idField = u.javaClass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(u, id)
    }

    private fun makeLinkToken(user: User, expired: Boolean = false): TelegramLinkToken {
        val expiresAt = if (expired) Instant.now().minusSeconds(60) else Instant.now().plusSeconds(300)
        return TelegramLinkToken(user = user, token = "token123", expiresAt = expiresAt)
    }

    // ── generateLinkToken ──────────────────────────────────────────────────────

    @Test
    fun `generateLinkToken - revokes existing tokens and saves new one`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId)

        every { linkTokenRepository.deleteAllByUserId(userId) } just Runs
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { linkTokenRepository.save(any()) } answers { firstArg() }

        val token = service.generateLinkToken(userId)

        assertNotNull(token)
        assertTrue(token.isNotBlank())
        verify { linkTokenRepository.deleteAllByUserId(userId) }
        verify { linkTokenRepository.save(any()) }
    }

    @Test
    fun `generateLinkToken - generated token has no hyphens`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId)

        every { linkTokenRepository.deleteAllByUserId(userId) } just Runs
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { linkTokenRepository.save(any()) } answers { firstArg() }

        val token = service.generateLinkToken(userId)

        assertFalse(token.contains("-"))
    }

    // ── linkAccount ────────────────────────────────────────────────────────────

    @Test
    fun `linkAccount - links telegram to user on valid token`() {
        val user = makeUser()
        val linkToken = makeLinkToken(user)

        every { linkTokenRepository.findByToken("token123") } returns linkToken
        every { userRepository.findByTelegramId(42L) } returns null
        every { userRepository.save(user) } returns user
        every { linkTokenRepository.delete(linkToken) } just Runs

        val result = service.linkAccount("token123", 42L, "telegram_user")

        assertEquals(42L, result.telegramId)
        assertEquals("telegram_user", result.telegramName)
        verify { userRepository.save(user) }
        verify { linkTokenRepository.delete(linkToken) }
    }

    @Test
    fun `linkAccount - throws for unknown token`() {
        every { linkTokenRepository.findByToken("bad-token") } returns null

        assertThrows<IllegalStateException> {
            service.linkAccount("bad-token", 42L, "user")
        }
    }

    @Test
    fun `linkAccount - throws and deletes expired token`() {
        val user = makeUser()
        val expiredToken = makeLinkToken(user, expired = true)

        every { linkTokenRepository.findByToken("token123") } returns expiredToken
        every { linkTokenRepository.delete(expiredToken) } just Runs

        assertThrows<IllegalStateException> {
            service.linkAccount("token123", 42L, "user")
        }
        verify { linkTokenRepository.delete(expiredToken) }
    }

    @Test
    fun `linkAccount - throws when telegram already linked to another user`() {
        val user = makeUser()
        val otherUser = makeUser()
        val linkToken = makeLinkToken(user)

        every { linkTokenRepository.findByToken("token123") } returns linkToken
        every { userRepository.findByTelegramId(42L) } returns otherUser

        assertThrows<IllegalStateException> {
            service.linkAccount("token123", 42L, "user")
        }
    }

    // ── unlinkAccount ──────────────────────────────────────────────────────────

    @Test
    fun `unlinkAccount - clears telegram fields`() {
        val user = makeUser().also {
            it.telegramId = 42L
            it.telegramName = "old_name"
        }

        every { userRepository.findByTelegramId(42L) } returns user
        every { userRepository.save(user) } returns user

        service.unlinkAccount(42L)

        assertNull(user.telegramId)
        assertNull(user.telegramName)
        verify { userRepository.save(user) }
    }

    @Test
    fun `unlinkAccount - throws when telegram id not linked`() {
        every { userRepository.findByTelegramId(99L) } returns null

        assertThrows<IllegalStateException> { service.unlinkAccount(99L) }
    }

    // ── unlinkByUserId ─────────────────────────────────────────────────────────

    @Test
    fun `unlinkByUserId - clears telegram fields for user`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId).also {
            it.telegramId = 42L
            it.telegramName = "old_name"
        }

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.save(user) } returns user

        service.unlinkByUserId(userId)

        assertNull(user.telegramId)
        assertNull(user.telegramName)
        verify { userRepository.save(user) }
    }

    @Test
    fun `unlinkByUserId - throws when user has no linked telegram`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId)

        every { userRepository.findById(userId) } returns Optional.of(user)

        assertThrows<IllegalStateException> { service.unlinkByUserId(userId) }
    }

    @Test
    fun `unlinkByUserId - throws when user not found`() {
        val userId = UUID.randomUUID()
        every { userRepository.findById(userId) } returns Optional.empty()

        assertThrows<NoSuchElementException> { service.unlinkByUserId(userId) }
    }
}
