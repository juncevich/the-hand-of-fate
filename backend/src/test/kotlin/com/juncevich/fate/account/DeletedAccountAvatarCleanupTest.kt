package com.juncevich.fate.account

import com.juncevich.fate.account.avatar.AvatarService
import com.juncevich.fate.account.avatar.DeletedAccountAvatarCleanup
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class DeletedAccountAvatarCleanupTest {
    @Test
    fun `a deleted account's avatar object is removed`() {
        val avatars = mockk<AvatarService>(relaxed = true)
        val userId = UserId(UUID.randomUUID())

        DeletedAccountAvatarCleanup(avatars).on(AccountDeleted(userId, Email.of("gone@test.com")))

        verify(exactly = 1) { avatars.deleteStoredObject(userId) }
    }
}
