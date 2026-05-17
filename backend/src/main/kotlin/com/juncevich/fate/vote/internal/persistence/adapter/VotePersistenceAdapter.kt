package com.juncevich.fate.vote.internal.persistence.adapter

import com.juncevich.fate.auth.UserQueryService
import com.juncevich.fate.vote.internal.domain.DrawHistory
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.domain.VoteOption
import com.juncevich.fate.vote.internal.domain.VoteParticipant
import com.juncevich.fate.vote.internal.persistence.entity.DrawHistoryJpaEntity
import com.juncevich.fate.vote.internal.persistence.jpa.DrawHistoryJpaRepository
import com.juncevich.fate.vote.internal.persistence.jpa.VoteJpaRepository
import com.juncevich.fate.vote.internal.persistence.jpa.VoteOptionJpaRepository
import com.juncevich.fate.vote.internal.persistence.jpa.VoteParticipantJpaRepository
import com.juncevich.fate.vote.internal.persistence.mapper.toDomain
import com.juncevich.fate.vote.internal.persistence.mapper.toJpaEntity
import com.juncevich.fate.vote.internal.port.DrawHistoryRepositoryPort
import com.juncevich.fate.vote.internal.port.ParticipantCount
import com.juncevich.fate.vote.internal.port.ParticipantRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteOptionRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteRepositoryPort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class VotePersistenceAdapter(
    private val voteJpaRepository: VoteJpaRepository,
    private val userQueryService: UserQueryService,
) : VoteRepositoryPort {
    override fun save(vote: Vote): Vote {
        val entity = voteJpaRepository.save(vote.toJpaEntity())
        return entity.toDomain(vote.creator)
    }

    override fun findById(id: UUID): Vote? {
        val entity = voteJpaRepository.findById(id).orElse(null) ?: return null
        val creator =
            userQueryService.findById(entity.creatorId)
                ?: throw NoSuchElementException("Creator not found for vote $id")
        return entity.toDomain(creator)
    }

    override fun findByIdForDraw(id: UUID): Vote? {
        val entity = voteJpaRepository.findByIdWithPessimisticLock(id) ?: return null
        val creator =
            userQueryService.findById(entity.creatorId)
                ?: throw NoSuchElementException("Creator not found for vote $id")
        return entity.toDomain(creator)
    }

    override fun findAllByUserIdOrParticipantEmail(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<Vote> {
        val page = voteJpaRepository.findAllByUserIdOrParticipantEmail(userId, email, pageable)
        val creatorsById =
            userQueryService
                .findAllByIdIn(page.content.map { it.creatorId }.distinct())
                .associateBy { it.id }
        return page.map { entity ->
            val creator =
                creatorsById[entity.creatorId]
                    ?: throw NoSuchElementException("Creator not found for vote ${entity.id}")
            entity.toDomain(creator)
        }
    }

    override fun delete(vote: Vote) {
        voteJpaRepository.deleteById(vote.id)
    }
}

@Component
class ParticipantPersistenceAdapter(
    private val voteJpaRepository: VoteJpaRepository,
    private val participantJpaRepository: VoteParticipantJpaRepository,
) : ParticipantRepositoryPort {
    override fun save(participant: VoteParticipant): VoteParticipant {
        val voteRef = voteJpaRepository.getReferenceById(participant.voteId)
        return participantJpaRepository.save(participant.toJpaEntity(voteRef)).toDomain()
    }

    override fun saveAll(participants: List<VoteParticipant>): List<VoteParticipant> {
        if (participants.isEmpty()) return emptyList()
        val voteRef = voteJpaRepository.getReferenceById(participants.first().voteId)
        return participantJpaRepository
            .saveAll(
                participants.map { it.toJpaEntity(voteRef) }
            ).map { it.toDomain() }
    }

    override fun findAllByVoteId(voteId: UUID): List<VoteParticipant> =
        participantJpaRepository.findAllByVoteId(voteId).map { it.toDomain() }

    override fun existsByVoteIdAndEmail(
        voteId: UUID,
        email: String,
    ): Boolean = participantJpaRepository.existsByVoteIdAndEmail(voteId, email)

    override fun deleteByVoteIdAndEmail(
        voteId: UUID,
        email: String,
    ) = participantJpaRepository.deleteByVoteIdAndEmail(voteId, email)

    override fun countByVoteIds(voteIds: List<UUID>): List<ParticipantCount> =
        participantJpaRepository.countByVoteIds(voteIds).map {
            ParticipantCount(it.voteId, it.participantCount)
        }

    override fun findEligibleEmailsForRound(
        voteId: UUID,
        round: Int,
    ): List<String> = participantJpaRepository.findEligibleEmailsForRound(voteId, round)
}

@Component
class VoteOptionPersistenceAdapter(
    private val voteJpaRepository: VoteJpaRepository,
    private val voteOptionJpaRepository: VoteOptionJpaRepository,
) : VoteOptionRepositoryPort {
    override fun save(option: VoteOption): VoteOption {
        val voteRef = voteJpaRepository.getReferenceById(option.voteId)
        return voteOptionJpaRepository.save(option.toJpaEntity(voteRef)).toDomain()
    }

    override fun saveAll(options: List<VoteOption>): List<VoteOption> {
        if (options.isEmpty()) return emptyList()
        val voteRef = voteJpaRepository.getReferenceById(options.first().voteId)
        return voteOptionJpaRepository
            .saveAll(
                options.map { it.toJpaEntity(voteRef) }
            ).map { it.toDomain() }
    }

    override fun findAllByVoteIdOrderedByPosition(voteId: UUID): List<VoteOption> =
        voteOptionJpaRepository.findAllByVoteIdOrderByPositionAscCreatedAtAsc(voteId).map { it.toDomain() }

    override fun deleteByVoteIdAndId(
        voteId: UUID,
        optionId: UUID,
    ) = voteOptionJpaRepository.deleteByVoteIdAndId(voteId, optionId)

    override fun findEligibleOptionsForRound(
        voteId: UUID,
        round: Int,
    ): List<VoteOption> = voteOptionJpaRepository.findEligibleOptionsForRound(voteId, round).map { it.toDomain() }
}

@Component
class DrawHistoryPersistenceAdapter(
    private val voteJpaRepository: VoteJpaRepository,
    private val voteOptionJpaRepository: VoteOptionJpaRepository,
    private val drawHistoryJpaRepository: DrawHistoryJpaRepository,
) : DrawHistoryRepositoryPort {
    override fun save(history: DrawHistory): DrawHistory {
        val voteRef = voteJpaRepository.getReferenceById(history.voteId)
        val entity =
            when (history) {
                is DrawHistory.ParticipantWinner -> {
                    DrawHistoryJpaEntity(
                        id = history.id,
                        vote = voteRef,
                        winnerEmail = history.email,
                        winnerDisplayName = history.displayName,
                        round = history.round,
                        drawnAt = history.drawnAt
                    )
                }

                is DrawHistory.OptionWinner -> {
                    DrawHistoryJpaEntity(
                        id = history.id,
                        vote = voteRef,
                        winnerOption = voteOptionJpaRepository.getReferenceById(history.optionId),
                        winnerOptionTitle = history.optionTitle,
                        round = history.round,
                        drawnAt = history.drawnAt
                    )
                }
            }
        return drawHistoryJpaRepository.save(entity).toDomain()
    }

    override fun findTopByVoteIdOrderByDrawnAtDesc(voteId: UUID): DrawHistory? =
        drawHistoryJpaRepository.findTopByVoteIdOrderByDrawnAtDesc(voteId)?.toDomain()

    override fun findAllByVoteIdOrderByDrawnAtDesc(voteId: UUID): List<DrawHistory> =
        drawHistoryJpaRepository.findAllByVoteIdOrderByDrawnAtDesc(voteId).map { it.toDomain() }
}
