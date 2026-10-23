package com.juncevich.fate.account

import com.juncevich.fate.account.domain.TelegramLinkToken
import com.juncevich.fate.account.domain.TelegramLinkTokenRepository
import com.juncevich.fate.account.domain.UserRepository
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.NotFoundException
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class TelegramLinkServiceTest {
    private val linkTokens = mockk<TelegramLinkTokenRepository>()
    private val userRepository = mockk<UserRepository>()

    private val service = TelegramLinkService(linkTokens, userRepository)

    private fun makeUser(id: UUID = UUID.randomUUID()) =
        User(
            id = id,
            email = "user@test.com",
            displayName = "Test User"
        )

    private fun makeLinkToken(
        user: User,
        expired: Boolean = false,
    ): TelegramLinkToken {
        val expiresAt = if (expired) Instant.now().minusSeconds(60) else Instant.now().plusSeconds(300)
        return TelegramLinkToken(userId = user.id, token = "token123", expiresAt = expiresAt)
    }

    @Test
    fun `generateLinkToken - revokes existing tokens and saves new one`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId)

        every { linkTokens.deleteAllByUserId(userId) } just Runs
        every { userRepository.findById(userId) } returns user
        every { linkTokens.insert(any()) } just Runs

        val result = service.generateLinkToken(userId)

        assertNotNull(result.token)
        assertTrue(result.token.isNotBlank())
        verify { linkTokens.deleteAllByUserId(userId) }
        verify { linkTokens.insert(match { it.userId == userId && it.token == result.token && it.expiresAt == result.expiresAt }) }
    }

    @Test
    fun `generateLinkToken - generated token has no hyphens`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId)

        every { linkTokens.deleteAllByUserId(userId) } just Runs
        every { userRepository.findById(userId) } returns user
        every { linkTokens.insert(any()) } just Runs

        val result = service.generateLinkToken(userId)

        assertFalse(result.token.contains("-"))
    }

    @Test
    fun `generateLinkToken - throws when user not found`() {
        val userId = UUID.randomUUID()
        every { linkTokens.deleteAllByUserId(userId) } just Runs
        every { userRepository.findById(userId) } returns null

        assertThrows<NotFoundException> { service.generateLinkToken(userId) }
        verify(exactly = 0) { linkTokens.insert(any()) }
    }

    @Test
    fun `linkAccount - allows relinking telegram already bound to the same user`() {
        val user = makeUser().linkTelegram(42L, "old_name")
        val linkToken = makeLinkToken(user)

        every { linkTokens.findByToken("token123") } returns linkToken
        every { userRepository.findByTelegramId(42L) } returns user
        every { userRepository.findById(user.id) } returns user
        every { userRepository.update(any()) } just Runs
        every { linkTokens.delete(linkToken) } just Runs

        val result = service.linkAccount("token123", 42L, "new_name")

        assertEquals("new_name", result.telegramName)
        verify { linkTokens.delete(linkToken) }
    }

    @Test
    fun `linkAccount - links telegram to user on valid token`() {
        val user = makeUser()
        val linkToken = makeLinkToken(user)

        every { linkTokens.findByToken("token123") } returns linkToken
        every { userRepository.findByTelegramId(42L) } returns null
        every { userRepository.findById(user.id) } returns user
        every { userRepository.update(any()) } just Runs
        every { linkTokens.delete(linkToken) } just Runs

        val result = service.linkAccount("token123", 42L, "telegram_user")

        assertEquals(42L, result.telegramId)
        assertEquals("telegram_user", result.telegramName)
        verify { userRepository.update(result) }
        verify { linkTokens.delete(linkToken) }
    }

    @Test
    fun `linkAccount - throws for unknown token`() {
        every { linkTokens.findByToken("bad-token") } returns null

        assertThrows<NotFoundException> {
            service.linkAccount("bad-token", 42L, "user")
        }
    }

    @Test
    fun `linkAccount - rejects group chat ids without consuming the token`() {
        listOf(0L, -1001234567890L).forEach { chatId ->
            assertThrows<BadRequestException> {
                service.linkAccount("token123", chatId, "group")
            }
        }
        verify(exactly = 0) { linkTokens.findByToken(any()) }
    }

    @Test
    fun `linkAccount - throws and deletes expired token`() {
        val user = makeUser()
        val expiredToken = makeLinkToken(user, expired = true)

        every { linkTokens.findByToken("token123") } returns expiredToken
        every { linkTokens.delete(expiredToken) } just Runs

        assertThrows<com.juncevich.fate.shared.BadRequestException> {
            service.linkAccount("token123", 42L, "user")
        }
        verify { linkTokens.delete(expiredToken) }
    }

    @Test
    fun `linkAccount - throws when telegram already linked to another user`() {
        val user = makeUser()
        val otherUser = makeUser()
        val linkToken = makeLinkToken(user)

        every { linkTokens.findByToken("token123") } returns linkToken
        every { userRepository.findByTelegramId(42L) } returns otherUser

        assertThrows<ConflictException> {
            service.linkAccount("token123", 42L, "user")
        }
    }

    @Test
    fun `unlinkAccount - clears telegram fields`() {
        val user = makeUser().linkTelegram(42L, "old_name")

        every { userRepository.findByTelegramId(42L) } returns user
        every { userRepository.update(any()) } just Runs

        service.unlinkAccount(42L)

        verify { userRepository.update(match { it.id == user.id && it.telegramId == null && it.telegramName == null }) }
    }

    @Test
    fun `unlinkAccount - throws when telegram id not linked`() {
        every { userRepository.findByTelegramId(99L) } returns null

        assertThrows<NotFoundException> { service.unlinkAccount(99L) }
    }

    @Test
    fun `unlinkByUserId - clears telegram fields for user`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId).linkTelegram(42L, "old_name")

        every { userRepository.findById(userId) } returns user
        every { userRepository.update(any()) } just Runs

        service.unlinkByUserId(userId)

        verify { userRepository.update(user.unlinkTelegram()) }
    }

    @Test
    fun `unlinkByUserId - throws when user has no linked telegram`() {
        val userId = UUID.randomUUID()
        val user = makeUser(userId)

        every { userRepository.findById(userId) } returns user

        assertThrows<ConflictException> { service.unlinkByUserId(userId) }
    }

    @Test
    fun `unlinkByUserId - throws when user not found`() {
        val userId = UUID.randomUUID()
        every { userRepository.findById(userId) } returns null

        assertThrows<NotFoundException> { service.unlinkByUserId(userId) }
    }
}
