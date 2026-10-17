package com.juncevich.fate.vote.internal.port

import com.juncevich.fate.vote.internal.domain.VoteOption
import java.util.UUID

interface VoteOptionRepositoryPort {
    fun save(option: VoteOption): VoteOption

    fun saveAll(options: List<VoteOption>): List<VoteOption>

    fun findAllByVoteIdOrderedByPosition(voteId: UUID): List<VoteOption>

    /** Sets each option's position to its index in [orderedIds]. */
    fun updatePositions(
        voteId: UUID,
        orderedIds: List<UUID>,
    )

    fun deleteByVoteIdAndId(
        voteId: UUID,
        optionId: UUID,
    )

    fun findEligibleOptionsForRound(
        voteId: UUID,
        round: Int,
    ): List<VoteOption>
}
