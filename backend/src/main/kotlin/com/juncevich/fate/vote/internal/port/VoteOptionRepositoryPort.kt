package com.juncevich.fate.vote.internal.port

import com.juncevich.fate.vote.internal.domain.VoteOption
import java.util.UUID

interface VoteOptionRepositoryPort {
    fun save(option: VoteOption): VoteOption

    fun saveAll(options: List<VoteOption>): List<VoteOption>

    fun findAllByVoteIdOrderedByPosition(voteId: UUID): List<VoteOption>

    fun deleteByVoteIdAndId(
        voteId: UUID,
        optionId: UUID,
    )

    fun findEligibleOptionsForRound(
        voteId: UUID,
        round: Int,
    ): List<VoteOption>
}
