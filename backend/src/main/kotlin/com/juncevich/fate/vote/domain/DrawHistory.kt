package com.juncevich.fate.vote.domain

import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.uuidV7
import com.juncevich.fate.vote.VoteId
import com.juncevich.fate.vote.Winner
import java.time.Instant
import java.util.UUID

sealed interface DrawHistory {
    val id: UUID
    val voteId: VoteId
    val round: Int
    val drawnAt: Instant

    /** `null` for draws recorded before audit records existed. */
    val audit: DrawAudit?

    data class ParticipantWinner(
        override val id: UUID = uuidV7(),
        override val voteId: VoteId,
        /** `null` once the winner deleted their account (see `AccountDeletionListener`). */
        val email: Email?,
        val displayName: String? = null,
        override val round: Int,
        override val drawnAt: Instant,
        override val audit: DrawAudit? = null,
    ) : DrawHistory

    data class OptionWinner(
        override val id: UUID = uuidV7(),
        override val voteId: VoteId,
        val optionId: UUID?,
        val optionTitle: String,
        override val round: Int,
        override val drawnAt: Instant,
        override val audit: DrawAudit? = null,
    ) : DrawHistory
}

/** The module API's view of who [this] draw picked. */
val DrawHistory.winner: Winner
    get() =
        when (this) {
            is DrawHistory.ParticipantWinner -> Winner.Participant(email?.value, displayName)
            is DrawHistory.OptionWinner -> Winner.Option(optionTitle)
        }
