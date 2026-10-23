package com.juncevich.fate.vote.persistence

import com.juncevich.fate.vote.domain.Participant
import com.juncevich.fate.vote.domain.Vote
import com.juncevich.fate.vote.domain.VoteOption
import com.juncevich.fate.vote.domain.VoteRepository
import com.juncevich.fate.vote.domain.VoteSummary
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Stores a [Vote] as its row plus child rows. A save writes the domain state onto the managed
 * entity — loaded earlier in the same transaction, so no extra query — and lets dirty checking,
 * cascading and orphan removal turn the differences into SQL.
 */
@Component
class VoteRepositoryAdapter(
    private val votes: VoteJpaRepository,
    private val em: EntityManager,
) : VoteRepository {
    override fun find(id: UUID): Vote? = votes.findById(id).orElse(null)?.toDomain()

    override fun findForUpdate(id: UUID): Vote? = votes.findByIdWithPessimisticLock(id)?.toDomain()

    override fun save(vote: Vote) {
        val entity = votes.findById(vote.id).orElse(null)
        if (entity == null) {
            // persist, not save(): with an assigned id, save() would merge and query the row first.
            em.persist(vote.toNewEntity())
            return
        }
        if (entity.version != vote.version) {
            throw ObjectOptimisticLockingFailureException(VoteJpaEntity::class.java, vote.id)
        }
        entity.status = vote.status
        entity.currentRound = vote.currentRound
        syncParticipants(entity, vote.participants)
        syncOptions(entity, vote.options)
    }

    override fun delete(id: UUID) = votes.deleteVote(id)

    override fun findSummaries(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<VoteSummary> {
        val page = votes.findAllByUserIdOrParticipantEmail(userId, email, pageable)
        val ids = page.content.map { it.id }
        val counts =
            if (ids.isEmpty()) {
                emptyMap()
            } else {
                votes
                    .countParticipants(
                        ids
                    ).associate { it.voteId to it.participantCount }
            }
        return page.map { it.toSummary(counts[it.id] ?: 0) }
    }

    override fun countByCreator(creatorId: UUID): Long = votes.countByCreatorId(creatorId)

    override fun removeParticipantEverywhere(email: String) = votes.deleteParticipantsByEmail(email)

    private fun syncParticipants(
        entity: VoteJpaEntity,
        participants: List<Participant>,
    ) {
        val wanted = participants.associateBy { it.id }
        entity.participants.removeIf { it.id !in wanted }
        val existing = entity.participants.associateBy { it.id }
        participants.forEach { participant ->
            val row = existing[participant.id]
            if (row ==
                null
            ) {
                entity.participants += participant.toEntity(entity)
            } else {
                row.displayName = participant.displayName
            }
        }
    }

    private fun syncOptions(
        entity: VoteJpaEntity,
        options: List<VoteOption>,
    ) {
        val wanted = options.associateBy { it.id }
        entity.options.removeIf { it.id !in wanted }
        val existing = entity.options.associateBy { it.id }
        options.forEach { option ->
            val row = existing[option.id]
            if (row == null) {
                entity.options += option.toEntity(entity)
            } else {
                // (vote_id, position) is checked at commit, so positions may swap meanwhile.
                row.title = option.title
                row.position = option.position
            }
        }
    }
}

private fun VoteJpaEntity.toDomain() =
    Vote(
        id = id,
        title = title,
        description = description,
        creatorId = creatorId,
        mode = mode,
        drawTarget = drawTarget,
        status = status,
        currentRound = currentRound,
        createdAt = createdAt,
        version = version,
        participants = participants.map { Participant(it.email, it.displayName, it.id, it.addedAt) },
        options = options.map { VoteOption(it.title, it.position, it.id, it.createdAt) }
    )

private fun VoteJpaEntity.toSummary(participantCount: Long) =
    VoteSummary(
        id = id,
        title = title,
        creatorId = creatorId,
        mode = mode,
        status = status,
        currentRound = currentRound,
        participantCount = participantCount,
        createdAt = createdAt
    )

private fun Vote.toNewEntity(): VoteJpaEntity {
    val entity =
        VoteJpaEntity(
            id = id,
            title = title,
            description = description,
            creatorId = creatorId,
            mode = mode,
            drawTarget = drawTarget,
            status = status,
            currentRound = currentRound,
            createdAt = createdAt
        )
    entity.participants += participants.map { it.toEntity(entity) }
    entity.options += options.map { it.toEntity(entity) }
    return entity
}

private fun Participant.toEntity(vote: VoteJpaEntity) =
    VoteParticipantJpaEntity(id = id, vote = vote, email = email, displayName = displayName, addedAt = addedAt)

private fun VoteOption.toEntity(vote: VoteJpaEntity) =
    VoteOptionJpaEntity(id = id, vote = vote, title = title, position = position, createdAt = createdAt)
