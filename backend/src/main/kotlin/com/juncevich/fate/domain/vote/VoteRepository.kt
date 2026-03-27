package com.juncevich.fate.domain.vote

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VoteRepository : JpaRepository<Vote, UUID> {

    @Query("""
        SELECT DISTINCT v FROM Vote v
        LEFT JOIN FETCH v.creator
        WHERE v.creator.id = :userId
           OR EXISTS (
               SELECT 1 FROM VoteParticipant p WHERE p.vote = v AND p.email = :email
           )
        ORDER BY v.createdAt DESC
    """)
    fun findAllByUserIdOrParticipantEmail(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<Vote>
}
