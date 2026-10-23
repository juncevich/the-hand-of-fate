package com.juncevich.fate.vote

import com.juncevich.fate.vote.port.VoteRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class VoteStatsService(
    private val voteRepositoryPort: VoteRepositoryPort,
) {
    /** How many votes [userId] created — what deleting the account would delete. */
    fun countCreatedBy(userId: UUID): Long = voteRepositoryPort.countByCreatorId(userId)
}
