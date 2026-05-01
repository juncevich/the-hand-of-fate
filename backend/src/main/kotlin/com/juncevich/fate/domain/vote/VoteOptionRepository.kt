package com.juncevich.fate.domain.vote

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VoteOptionRepository : JpaRepository<VoteOption, UUID> {

    fun findAllByVoteIdOrderByPositionAscCreatedAtAsc(voteId: UUID): List<VoteOption>

    fun countByVoteId(voteId: UUID): Long

    @Query("""
        SELECT o FROM VoteOption o
        WHERE o.vote.id = :voteId
          AND o.id NOT IN (
              SELECT h.winnerOption.id FROM DrawHistory h
              WHERE h.vote.id = :voteId AND h.round = :round
              AND h.winnerOption IS NOT NULL
          )
        ORDER BY o.position ASC, o.createdAt ASC
    """)
    fun findEligibleOptionsForRound(voteId: UUID, round: Int): List<VoteOption>
}
