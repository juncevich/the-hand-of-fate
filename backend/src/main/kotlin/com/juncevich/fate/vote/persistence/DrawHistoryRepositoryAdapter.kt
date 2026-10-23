package com.juncevich.fate.vote.persistence

import com.juncevich.fate.vote.domain.DrawAudit
import com.juncevich.fate.vote.domain.DrawHistory
import com.juncevich.fate.vote.domain.DrawHistoryRepository
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Transactional like Spring Data's own repositories: inside a service transaction it joins it; called
// without one (AvatarService keeps storage I/O out of transactions) it still loads, writes and flushes.
@Component
@Transactional
class DrawHistoryRepositoryAdapter(
    private val history: DrawHistoryJpaRepository,
    private val em: EntityManager,
) : DrawHistoryRepository {
    // Entries are only ever inserted: persist, so the assigned id causes no lookup first.
    override fun save(entry: DrawHistory) = em.persist(entry.toEntity())

    override fun findLatest(voteId: UUID): DrawHistory? =
        history.findTopByVoteIdOrderByDrawnAtDescIdDesc(voteId)?.toDomain()

    override fun findPage(
        voteId: UUID,
        pageable: Pageable,
    ): Page<DrawHistory> = history.findAllByVoteIdOrderByDrawnAtDescIdDesc(voteId, pageable).map { it.toDomain() }

    override fun findByRound(
        voteId: UUID,
        round: Int,
    ): List<DrawHistory> = history.findAllByVoteIdAndRound(voteId, round).map { it.toDomain() }

    override fun anonymizeWins(
        email: String,
        displayName: String,
    ) = history.anonymizeWins(email, displayName)

    private fun DrawHistory.toEntity(): DrawHistoryJpaEntity {
        val vote = em.getReference(VoteJpaEntity::class.java, voteId)
        val audit = audit?.let { DrawAuditColumns(it.seed, it.candidates) }
        return when (this) {
            is DrawHistory.ParticipantWinner -> {
                DrawHistoryJpaEntity(
                    id = id,
                    vote = vote,
                    winnerEmail = email,
                    winnerDisplayName = displayName,
                    round = round,
                    drawnAt = drawnAt,
                    audit = audit
                )
            }

            is DrawHistory.OptionWinner -> {
                DrawHistoryJpaEntity(
                    id = id,
                    vote = vote,
                    winnerOption = optionId?.let { em.getReference(VoteOptionJpaEntity::class.java, it) },
                    winnerOptionTitle = optionTitle,
                    round = round,
                    drawnAt = drawnAt,
                    audit = audit
                )
            }
        }
    }
}

private fun DrawHistoryJpaEntity.toDomain(): DrawHistory {
    val optionTitle = winnerOptionTitle
    val audit = audit?.let { DrawAudit(it.seed, it.candidates) }
    // A participant win whose winner deleted their account keeps neither address nor option.
    return if (optionTitle != null) {
        DrawHistory.OptionWinner(
            id = id,
            voteId = vote.id,
            optionId = winnerOption?.id,
            optionTitle = optionTitle,
            round = round,
            drawnAt = drawnAt,
            audit = audit
        )
    } else {
        DrawHistory.ParticipantWinner(
            id = id,
            voteId = vote.id,
            email = winnerEmail,
            displayName = winnerDisplayName,
            round = round,
            drawnAt = drawnAt,
            audit = audit
        )
    }
}
