package com.juncevich.fate.vote.listener

import com.juncevich.fate.account.AccountDeleted
import com.juncevich.fate.vote.domain.DrawHistoryRepository
import com.juncevich.fate.vote.domain.VoteRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

const val DELETED_USER_NAME = "Deleted user"

/**
 * Forgets a deleted account in other people's votes: it stops being a participant, and its past
 * wins stay in the history (so FAIR_ROTATION rounds and results still add up) without the address.
 * Its own votes go with the account (`votes.creator_id ON DELETE CASCADE`).
 */
@Component
class AccountDeletionListener(
    private val votes: VoteRepository,
    private val history: DrawHistoryRepository,
) {
    @EventListener
    fun on(event: AccountDeleted) {
        votes.removeParticipantEverywhere(event.email)
        history.anonymizeWins(event.email, DELETED_USER_NAME)
    }
}
