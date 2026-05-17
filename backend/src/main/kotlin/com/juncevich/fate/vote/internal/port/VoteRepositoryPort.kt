package com.juncevich.fate.vote.internal.port

import com.juncevich.fate.vote.internal.domain.Vote
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface VoteRepositoryPort {
    fun save(vote: Vote): Vote

    fun findById(id: UUID): Vote?

    fun findAllByUserIdOrParticipantEmail(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<Vote>

    fun delete(vote: Vote)
}
