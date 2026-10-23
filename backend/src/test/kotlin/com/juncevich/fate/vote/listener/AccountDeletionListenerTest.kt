package com.juncevich.fate.vote.listener

import com.juncevich.fate.account.AccountDeleted
import com.juncevich.fate.vote.domain.DrawHistoryRepository
import com.juncevich.fate.vote.domain.VoteRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class AccountDeletionListenerTest {
    private val votes = mockk<VoteRepository>(relaxed = true)
    private val history = mockk<DrawHistoryRepository>(relaxed = true)

    @Test
    fun `a deleted account leaves other votes and keeps its wins anonymously`() {
        AccountDeletionListener(votes, history).on(AccountDeleted(UUID.randomUUID(), "gone@test.com"))

        verify { votes.removeParticipantEverywhere("gone@test.com") }
        verify { history.anonymizeWins("gone@test.com", DELETED_USER_NAME) }
    }
}
