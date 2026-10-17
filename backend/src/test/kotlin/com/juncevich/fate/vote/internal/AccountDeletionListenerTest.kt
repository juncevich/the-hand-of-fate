package com.juncevich.fate.vote.internal

import com.juncevich.fate.auth.AccountDeleted
import com.juncevich.fate.vote.internal.port.AccountCleanupPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class AccountDeletionListenerTest {
    private val cleanup = mockk<AccountCleanupPort>(relaxed = true)

    @Test
    fun `a deleted account leaves other votes and keeps its wins anonymously`() {
        AccountDeletionListener(cleanup).on(AccountDeleted(UUID.randomUUID(), "gone@test.com"))

        verify { cleanup.removeParticipant("gone@test.com") }
        verify { cleanup.anonymizeWins("gone@test.com", DELETED_USER_NAME) }
    }
}
