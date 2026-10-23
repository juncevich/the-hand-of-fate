package com.juncevich.fate.account

import com.juncevich.fate.account.port.UserRepositoryPort
import com.juncevich.fate.account.service.AvatarImageProcessor
import com.juncevich.fate.account.service.AvatarService
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.storage.ObjectStorage
import com.juncevich.fate.storage.StorageUnavailableException
import com.juncevich.fate.storage.StoredObject
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class AvatarServiceTest {
    private val users = mockk<UserRepositoryPort>()
    private val storage = mockk<ObjectStorage>()
    private val processor = mockk<AvatarImageProcessor>()
    private val service = AvatarService(users, storage, processor)

    private val user = User(email = "user@test.com", displayName = "User")
    private val key = "avatars/${user.id}.png"
    private val png = byteArrayOf(1, 2, 3)

    @BeforeEach
    fun setUp() {
        every { storage.isEnabled } returns true
        every { users.findById(user.id) } returns user
        every { users.save(any()) } answers { firstArg() }
    }

    @Test
    fun `upload stores the processed image before pointing the user at it`() {
        every { processor.toAvatarPng(any()) } returns png
        every { storage.put(key, png, "image/png") } just runs
        val saved = slot<User>()
        every { users.save(capture(saved)) } answers { firstArg() }

        val url = service.upload(user.id, byteArrayOf(9))

        val version = checkNotNull(saved.captured.avatarUpdatedAt).toEpochMilli()
        assertEquals("/api/v1/users/${user.id}/avatar?v=$version", url)
        verifyOrder {
            storage.put(key, png, "image/png")
            users.save(any())
        }
    }

    @Test
    fun `upload does not touch the user when the image is rejected`() {
        every { processor.toAvatarPng(any()) } throws IllegalArgumentException("bad")

        assertThrows<IllegalArgumentException> { service.upload(user.id, byteArrayOf(9)) }

        verify(exactly = 0) { storage.put(any(), any(), any()) }
        verify(exactly = 0) { users.save(any()) }
    }

    @Test
    fun `upload and delete fail when storage is not configured`() {
        every { storage.isEnabled } returns false

        assertThrows<StorageUnavailableException> { service.upload(user.id, byteArrayOf(9)) }
        assertThrows<StorageUnavailableException> { service.delete(user.id) }
    }

    @Test
    fun `upload fails for an unknown user`() {
        every { users.findById(user.id) } returns null

        assertThrows<NotFoundException> { service.upload(user.id, byteArrayOf(9)) }
    }

    @Test
    fun `delete clears the avatar and removes the object`() {
        user.avatarUpdatedAt = Instant.now()
        every { storage.delete(key) } just runs

        service.delete(user.id)

        assertNull(user.avatarUpdatedAt)
        verifyOrder {
            users.save(user)
            storage.delete(key)
        }
    }

    @Test
    fun `delete succeeds even if the object cannot be removed`() {
        user.avatarUpdatedAt = Instant.now()
        every { storage.delete(key) } throws IllegalStateException("store down")

        service.delete(user.id)

        assertNull(user.avatarUpdatedAt)
        verify { users.save(user) }
    }

    @Test
    fun `delete is a no-op without an avatar`() {
        service.delete(user.id)

        verify(exactly = 0) { users.save(any()) }
        verify(exactly = 0) { storage.delete(any()) }
    }

    @Test
    fun `load returns the stored image with its version`() {
        user.avatarUpdatedAt = Instant.ofEpochMilli(42)
        every { storage.get(key) } returns StoredObject(png, "image/png")

        val avatar = service.load(user.id)

        assertArrayEquals(png, avatar.content)
        assertEquals("image/png", avatar.contentType)
        assertEquals(42, avatar.version)
    }

    @Test
    fun `load reports a missing avatar as not found`() {
        assertTrue(assertThrows<NotFoundException> { service.load(user.id) }.message == "Avatar not found")

        user.avatarUpdatedAt = Instant.now()
        every { storage.get(key) } returns null
        assertThrows<NotFoundException> { service.load(user.id) }

        every { storage.isEnabled } returns false
        assertThrows<NotFoundException> { service.load(user.id) }

        every { users.findById(user.id) } returns null
        assertThrows<NotFoundException> { service.load(user.id) }
    }
}
