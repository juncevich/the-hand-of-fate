package com.juncevich.fate.vote.internal.persistence.jpa

import com.juncevich.fate.vote.internal.persistence.entity.VoteJpaEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VoteJpaRepository : JpaRepository<VoteJpaEntity, UUID> {
    @Query(
        """
        SELECT DISTINCT v FROM VoteJpaEntity v
        WHERE v.creatorId = :userId
           OR EXISTS (
               SELECT 1 FROM VoteParticipantJpaEntity p WHERE p.vote = v AND p.email = :email
           )
        ORDER BY v.createdAt DESC
        """
    )
    fun findAllByUserIdOrParticipantEmail(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<VoteJpaEntity>
}
