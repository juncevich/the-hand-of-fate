package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class UserQueryServiceTest {
    private val repository = mockk<UserRepositoryPort>()
    private val service = UserQueryService(repository)

    @Test
    fun `public profile lookup preserves missing identities instead of creating an empty profile`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns null
        every { repository.findByEmail("missing@test.com") } returns null
        assertNull(service.findProfileById(id))
        assertNull(service.findProfileByEmail("missing@test.com"))
    }

    @Test
    fun `bulk profile lookup exports only public identity fields`() {
        val user = User(email = "user@test.com", passwordHash = "secret hash", displayName = "User", telegramId = 42)
        every { repository.findAllByIdIn(listOf(user.id)) } returns listOf(user)
        every { repository.findAllByEmailIn(listOf(user.email)) } returns listOf(user)
        val profile = UserProfile(user.id, user.email, user.displayName)
        assertEquals(listOf(profile), service.findProfilesByIdIn(listOf(user.id)))
        assertEquals(listOf(profile), service.findProfilesByEmailIn(listOf(user.email)))
        user.displayName = "Changed"
        assertEquals("User", profile.displayName)
    }
}
