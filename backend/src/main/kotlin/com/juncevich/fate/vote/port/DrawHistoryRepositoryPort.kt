package com.juncevich.fate.vote.port

import com.juncevich.fate.vote.domain.DrawHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface DrawHistoryRepositoryPort {
    fun save(history: DrawHistory): DrawHistory

    fun findTopByVoteIdOrderByDrawnAtDescIdDesc(voteId: UUID): DrawHistory?

    fun findAllByVoteIdOrderByDrawnAtDescIdDesc(
        voteId: UUID,
        pageable: Pageable,
    ): Page<DrawHistory>
}
