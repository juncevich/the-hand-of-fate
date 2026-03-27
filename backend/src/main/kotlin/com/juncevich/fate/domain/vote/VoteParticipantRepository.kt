package com.juncevich.fate.domain.vote

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VoteParticipantRepository : JpaRepository<VoteParticipant, UUID> {

    fun findAllByVoteId(voteId: UUID): List<VoteParticipant>

    fun existsByVoteIdAndEmail(voteId: UUID, email: String): Boolean

    fun deleteByVoteIdAndEmail(voteId: UUID, email: String)

    @Query("""
        SELECT p.email FROM VoteParticipant p
        WHERE p.vote.id = :voteId
          AND p.email NOT IN (
              SELECT h.winnerEmail FROM DrawHistory h
              WHERE h.vote.id = :voteId AND h.round = :round
          )
    """)
    fun findEligibleEmailsForRound(voteId: UUID, round: Int): List<String>

    @Query("SELECT COUNT(p) FROM VoteParticipant p WHERE p.vote.id = :voteId")
    fun countByVoteId(voteId: UUID): Long
}
