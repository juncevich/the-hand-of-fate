package com.juncevich.fate.vote.listener

import com.juncevich.fate.account.AccountDeleted
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
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
        AccountDeletionListener(votes, history).on(AccountDeleted(UserId(UUID.randomUUID()), Email.of("gone@test.com")))

        verify { votes.removeParticipantEverywhere(Email.of("gone@test.com")) }
        verify { history.anonymizeWins(Email.of("gone@test.com"), DELETED_USER_NAME) }
    }
}
