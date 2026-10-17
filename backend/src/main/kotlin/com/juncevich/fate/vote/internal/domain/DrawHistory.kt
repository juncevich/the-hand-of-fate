package com.juncevich.fate.vote.internal.domain

import com.juncevich.fate.shared.uuidV7
import com.juncevich.fate.vote.internal.DrawAudit
import java.time.Instant
import java.util.UUID

sealed interface DrawHistory {
    val id: UUID
    val voteId: UUID
    val round: Int
    val drawnAt: Instant

    /** `null` for draws recorded before audit records existed. */
    val audit: DrawAudit?

    data class ParticipantWinner(
        override val id: UUID = uuidV7(),
        override val voteId: UUID,
        val email: String,
        val displayName: String? = null,
        override val round: Int,
        override val drawnAt: Instant = Instant.now(),
        override val audit: DrawAudit? = null,
    ) : DrawHistory

    data class OptionWinner(
        override val id: UUID = uuidV7(),
        override val voteId: UUID,
        val optionId: UUID?,
        val optionTitle: String,
        override val round: Int,
        override val drawnAt: Instant = Instant.now(),
        override val audit: DrawAudit? = null,
    ) : DrawHistory
}
