package com.juncevich.fate.vote.internal.persistence.jpa

import com.juncevich.fate.vote.internal.persistence.entity.VoteParticipantJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VoteParticipantJpaRepository : JpaRepository<VoteParticipantJpaEntity, UUID> {
    fun findAllByVoteId(voteId: UUID): List<VoteParticipantJpaEntity>

    fun existsByVoteIdAndEmail(
        voteId: UUID,
        email: String,
    ): Boolean

    fun deleteByVoteIdAndEmail(
        voteId: UUID,
        email: String,
    )

    @Query(
        """
        SELECT p.email FROM VoteParticipantJpaEntity p
        WHERE p.vote.id = :voteId
          AND p.email NOT IN (
              SELECT h.winnerEmail FROM DrawHistoryJpaEntity h
              WHERE h.vote.id = :voteId AND h.round = :round
              AND h.winnerEmail IS NOT NULL
          )
        """
    )
    fun findEligibleEmailsForRound(
        voteId: UUID,
        round: Int,
    ): List<String>

    @Query(
        """
        SELECT p.vote.id AS voteId, COUNT(p) AS participantCount
        FROM VoteParticipantJpaEntity p
        WHERE p.vote.id IN :voteIds
        GROUP BY p.vote.id
        """
    )
    fun countByVoteIds(voteIds: Collection<UUID>): List<VoteParticipantCountProjection>
}

interface VoteParticipantCountProjection {
    val voteId: UUID
    val participantCount: Long
}
