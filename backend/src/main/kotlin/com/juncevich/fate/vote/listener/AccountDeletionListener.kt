package com.juncevich.fate.vote.listener

import com.juncevich.fate.account.AccountDeleted
import com.juncevich.fate.vote.port.AccountCleanupPort
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
    private val cleanup: AccountCleanupPort,
) {
    @EventListener
    fun on(event: AccountDeleted) {
        cleanup.removeParticipant(event.email)
        cleanup.anonymizeWins(event.email, DELETED_USER_NAME)
    }
}
