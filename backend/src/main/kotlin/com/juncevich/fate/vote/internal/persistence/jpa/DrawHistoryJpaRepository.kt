package com.juncevich.fate.vote.internal.persistence.jpa

import com.juncevich.fate.vote.internal.persistence.entity.DrawHistoryJpaEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DrawHistoryJpaRepository : JpaRepository<DrawHistoryJpaEntity, UUID> {
    fun findAllByVoteIdOrderByDrawnAtDescIdDesc(
        voteId: UUID,
        pageable: Pageable,
    ): Page<DrawHistoryJpaEntity>

    fun findTopByVoteIdOrderByDrawnAtDescIdDesc(voteId: UUID): DrawHistoryJpaEntity?
}
