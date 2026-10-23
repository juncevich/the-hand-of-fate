package com.juncevich.fate.vote.persistence

import com.juncevich.fate.shared.Email
import com.juncevich.fate.vote.VoteId
import com.juncevich.fate.vote.domain.DrawAudit
import com.juncevich.fate.vote.domain.DrawHistory
import com.juncevich.fate.vote.domain.DrawHistoryRepository
import com.juncevich.fate.vote.domain.PageQuery
import com.juncevich.fate.vote.domain.PageResult
import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
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

    override fun findLatest(voteId: VoteId): DrawHistory? =
        history.findTopByVoteIdOrderByDrawnAtDescIdDesc(voteId.value)?.toDomain()

    override fun findPage(
        voteId: VoteId,
        page: PageQuery,
    ): PageResult<DrawHistory> {
        val rows = history.findAllByVoteIdOrderByDrawnAtDescIdDesc(voteId.value, PageRequest.of(page.number, page.size))
        return PageResult(rows.content.map { it.toDomain() }, page, rows.totalElements)
    }

    override fun findByRound(
        voteId: VoteId,
        round: Int,
    ): List<DrawHistory> = history.findAllByVoteIdAndRound(voteId.value, round).map { it.toDomain() }

    override fun anonymizeWins(
        email: Email,
        displayName: String,
    ) = history.anonymizeWins(email.value, displayName)

    private fun DrawHistory.toEntity(): DrawHistoryJpaEntity {
        val vote = em.getReference(VoteJpaEntity::class.java, voteId.value)
        val audit = audit?.let { DrawAuditColumns(it.seed, it.candidates) }
        return when (this) {
            is DrawHistory.ParticipantWinner -> {
                DrawHistoryJpaEntity(
                    id = id,
                    vote = vote,
                    winnerEmail = email?.value,
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
            voteId = VoteId(vote.id),
            optionId = winnerOption?.id,
            optionTitle = optionTitle,
            round = round,
            drawnAt = drawnAt,
            audit = audit
        )
    } else {
        DrawHistory.ParticipantWinner(
            id = id,
            voteId = VoteId(vote.id),
            email = winnerEmail?.let(Email::trusted),
            displayName = winnerDisplayName,
            round = round,
            drawnAt = drawnAt,
            audit = audit
        )
    }
}
