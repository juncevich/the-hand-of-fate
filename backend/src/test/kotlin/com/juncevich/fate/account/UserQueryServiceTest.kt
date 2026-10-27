package com.juncevich.fate.account

import com.juncevich.fate.NOW
import com.juncevich.fate.account.domain.User
import com.juncevich.fate.account.domain.UserRepository
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class UserQueryServiceTest {
    private val repository = mockk<UserRepository>()
    private val service = UserQueryService(repository)

    @Test
    fun `public profile lookup preserves missing identities instead of creating an empty profile`() {
        val id = UserId(UUID.randomUUID())
        every { repository.findById(id) } returns null
        every { repository.findByEmail(Email.of("missing@test.com")) } returns null
        assertNull(service.findProfileById(id))
        assertNull(service.findProfileByEmail(Email.of("missing@test.com")))
    }

    @Test
    fun `bulk profile lookup exports only public identity fields`() {
        val user = User(id = UserId(UUID.randomUUID()), email = Email.of("user@test.com"), displayName = "User", telegramId = 42, createdAt = NOW)
        every { repository.findAllByIdIn(listOf(user.id)) } returns listOf(user)
        every { repository.findAllByEmailIn(listOf(user.email)) } returns listOf(user)
        val profile = UserProfile(user.id, user.email, user.displayName)
        assertEquals(listOf(profile), service.findProfilesByIdIn(listOf(user.id)))
        assertEquals(listOf(profile), service.findProfilesByEmailIn(listOf(user.email)))
    }
}
