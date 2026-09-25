package com.juncevich.fate.vote.internal.domain

import com.juncevich.fate.shared.uuidV7
import java.time.Instant
import java.util.UUID

sealed interface DrawHistory {
    val id: UUID
    val voteId: UUID
    val round: Int
    val drawnAt: Instant

    data class ParticipantWinner(
        override val id: UUID = uuidV7(),
        override val voteId: UUID,
        val email: String,
        val displayName: String? = null,
        override val round: Int,
        override val drawnAt: Instant = Instant.now(),
    ) : DrawHistory

    data class OptionWinner(
        override val id: UUID = uuidV7(),
        override val voteId: UUID,
        val optionId: UUID,
        val optionTitle: String,
        override val round: Int,
        override val drawnAt: Instant = Instant.now(),
    ) : DrawHistory
}
