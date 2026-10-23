package com.juncevich.fate.vote

import com.juncevich.fate.shared.UserId
import com.juncevich.fate.vote.domain.VoteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class VoteStatsService(
    private val votes: VoteRepository,
) {
    /** How many votes [userId] created — what deleting the account would delete. */
    fun countCreatedBy(userId: UserId): Long = votes.countByCreator(userId)
}
