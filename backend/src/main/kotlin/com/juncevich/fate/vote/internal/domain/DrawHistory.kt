package com.juncevich.fate.vote.internal.domain

import java.time.Instant
import java.util.UUID

sealed class DrawHistory {
    abstract val id: UUID
    abstract val voteId: UUID
    abstract val round: Int
    abstract val drawnAt: Instant

    data class ParticipantWinner(
        override val id: UUID = UUID.randomUUID(),
        override val voteId: UUID,
        val email: String,
        val displayName: String? = null,
        override val round: Int,
        override val drawnAt: Instant = Instant.now(),
    ) : DrawHistory()

    data class OptionWinner(
        override val id: UUID = UUID.randomUUID(),
        override val voteId: UUID,
        val optionId: UUID,
        val optionTitle: String,
        override val round: Int,
        override val drawnAt: Instant = Instant.now(),
    ) : DrawHistory()
}
