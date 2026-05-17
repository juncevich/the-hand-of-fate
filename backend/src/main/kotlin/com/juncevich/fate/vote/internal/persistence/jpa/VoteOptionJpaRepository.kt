package com.juncevich.fate.vote.internal.persistence.jpa

import com.juncevich.fate.vote.internal.persistence.entity.VoteOptionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VoteOptionJpaRepository : JpaRepository<VoteOptionJpaEntity, UUID> {
    fun findAllByVoteIdOrderByPositionAscCreatedAtAsc(voteId: UUID): List<VoteOptionJpaEntity>

    fun countByVoteId(voteId: UUID): Long

    fun deleteByVoteIdAndId(
        voteId: UUID,
        id: UUID,
    )

    @Query(
        """
        SELECT o FROM VoteOptionJpaEntity o
        WHERE o.vote.id = :voteId
          AND o.id NOT IN (
              SELECT h.winnerOption.id FROM DrawHistoryJpaEntity h
              WHERE h.vote.id = :voteId AND h.round = :round
              AND h.winnerOption IS NOT NULL
          )
        ORDER BY o.position ASC, o.createdAt ASC
        """
    )
    fun findEligibleOptionsForRound(
        voteId: UUID,
        round: Int,
    ): List<VoteOptionJpaEntity>
}
