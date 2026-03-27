package com.juncevich.fate.domain.vote

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DrawHistoryRepository : JpaRepository<DrawHistory, UUID> {

    fun findAllByVoteIdOrderByDrawnAtDesc(voteId: UUID): List<DrawHistory>

    fun findTopByVoteIdOrderByDrawnAtDesc(voteId: UUID): DrawHistory?

    fun countByVoteIdAndRound(voteId: UUID, round: Int): Long
}
