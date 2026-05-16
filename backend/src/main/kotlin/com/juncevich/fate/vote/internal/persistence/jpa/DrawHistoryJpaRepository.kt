package com.juncevich.fate.vote.internal.persistence.jpa

import com.juncevich.fate.vote.internal.persistence.entity.DrawHistoryJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DrawHistoryJpaRepository : JpaRepository<DrawHistoryJpaEntity, UUID> {
    fun findAllByVoteIdOrderByDrawnAtDesc(voteId: UUID): List<DrawHistoryJpaEntity>
    fun findTopByVoteIdOrderByDrawnAtDesc(voteId: UUID): DrawHistoryJpaEntity?
}
