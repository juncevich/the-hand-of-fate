package com.juncevich.fate.vote.internal.port

import com.juncevich.fate.vote.internal.domain.DrawHistory
import java.util.UUID

interface DrawHistoryRepositoryPort {
    fun save(history: DrawHistory): DrawHistory
    fun findTopByVoteIdOrderByDrawnAtDesc(voteId: UUID): DrawHistory?
    fun findAllByVoteIdOrderByDrawnAtDesc(voteId: UUID): List<DrawHistory>
}
