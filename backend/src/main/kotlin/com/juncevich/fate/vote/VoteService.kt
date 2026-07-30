package com.juncevich.fate.vote

import com.juncevich.fate.auth.UserQueryService
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.requireValidEmail
import com.juncevich.fate.vote.internal.DrawService
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.domain.VoteOption
import com.juncevich.fate.vote.internal.domain.VoteParticipant
import com.juncevich.fate.vote.internal.port.DrawHistoryRepositoryPort
import com.juncevich.fate.vote.internal.port.NotificationPort
import com.juncevich.fate.vote.internal.port.ParticipantRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteOptionRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteRepositoryPort
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@Service
@Transactional
class VoteService(
    private val voteRepositoryPort: VoteRepositoryPort,
    private val participantRepositoryPort: ParticipantRepositoryPort,
    private val voteOptionRepositoryPort: VoteOptionRepositoryPort,
    private val drawHistoryRepositoryPort: DrawHistoryRepositoryPort,
    private val userQueryService: UserQueryService,
    private val drawService: DrawService,
    private val notificationPort: NotificationPort,
    private val meterRegistry: MeterRegistry,
) {
    fun createVote(
        creatorId: UUID,
        request: CreateVoteCommand,
    ): VoteDetailDto {
        val creator = userQueryService.findById(creatorId) ?: throw NoSuchElementException("User not found")

        request.participantEmails.forEach { requireValidEmail(it.trim()) }

        val vote =
            voteRepositoryPort.save(
                Vote(title = request.title, description = request.description, creator = creator, mode = request.mode)
            )

        val allEmails = setOf(creator.email) + request.participantEmails
        val existingUsers = userQueryService.findAllByEmailIn(allEmails).associateBy { it.email }
        val participants =
            participantRepositoryPort.saveAll(
                allEmails.map { email ->
                    VoteParticipant(voteId = vote.id, email = email, displayName = existingUsers[email]?.displayName)
                }
            )

        val options =
            voteOptionRepositoryPort.saveAll(
                (request.options ?: emptyList())
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .mapIndexed { index, title -> VoteOption(voteId = vote.id, title = title, position = index) }
            )

        meterRegistry.counter("vote.created", "mode", vote.mode.name).increment()

        request.participantEmails.forEach { email ->
            afterCommit { notificationPort.notifyVoteInvitation(email, vote) }
        }

        return vote.toDetailDto(participants, options, null, creator.id)
    }

    @Transactional(readOnly = true)
    fun listVotes(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<VoteSummaryDto> {
        val votes = voteRepositoryPort.findAllByUserIdOrParticipantEmail(userId, email, pageable)
        val voteIds = votes.content.map { it.id }
        val participantCounts =
            if (voteIds.isEmpty()) {
                emptyMap()
            } else {
                participantRepositoryPort.countByVoteIds(voteIds).associate { it.voteId to it.participantCount }
            }
        return votes.map { vote ->
            vote.toSummaryDto(participantCounts[vote.id] ?: 0, vote.creator.id == userId)
        }
    }

    @Transactional(readOnly = true)
    fun getVote(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): VoteDetailDto {
        val vote = getVoteOrThrow(voteId)
        checkCanView(vote, requesterId, requesterEmail)
        val participants = participantRepositoryPort.findAllByVoteId(voteId)
        val options = voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(voteId)
        val lastResult = drawHistoryRepositoryPort.findTopByVoteIdOrderByDrawnAtDesc(voteId)
        return vote.toDetailDto(participants, options, lastResult, requesterId)
    }

    fun addParticipant(
        voteId: UUID,
        requesterId: UUID,
        email: String,
    ) {
        val vote = getVoteOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        requireValidEmail(email.trim())
        check(vote.status == VoteStatus.PENDING) { "Cannot add participants to a non-pending vote" }
        check(!participantRepositoryPort.existsByVoteIdAndEmail(voteId, email)) { "Participant already exists" }

        val user = userQueryService.findByEmail(email)
        participantRepositoryPort.save(VoteParticipant(voteId = voteId, email = email, displayName = user?.displayName))

        afterCommit { notificationPort.notifyVoteInvitation(email, vote) }
    }

    fun removeParticipant(
        voteId: UUID,
        requesterId: UUID,
        email: String,
    ) {
        val vote = getVoteOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        check(vote.status == VoteStatus.PENDING) { "Cannot modify a non-pending vote" }
        participantRepositoryPort.deleteByVoteIdAndEmail(voteId, email)
    }

    fun addOption(
        voteId: UUID,
        requesterId: UUID,
        title: String,
    ) {
        val vote = getVoteOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        check(vote.status == VoteStatus.PENDING) { "Cannot add options to a non-pending vote" }
        // Use Int.MAX_VALUE so new options always sort after batch-created ones (ordered by createdAt as tiebreaker).
        voteOptionRepositoryPort.save(VoteOption(voteId = voteId, title = title.trim(), position = Int.MAX_VALUE))
    }

    fun removeOption(
        voteId: UUID,
        requesterId: UUID,
        optionId: UUID,
    ) {
        val vote = getVoteOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        check(vote.status == VoteStatus.PENDING) { "Cannot modify a non-pending vote" }
        voteOptionRepositoryPort.deleteByVoteIdAndId(voteId, optionId)
    }

    fun draw(
        voteId: UUID,
        requesterId: UUID,
    ): DrawResult {
        val vote = voteRepositoryPort.findByIdForDraw(voteId) ?: throw NoSuchElementException("Vote not found")
        checkIsCreator(vote, requesterId)

        val result = drawService.draw(vote)

        val participants = participantRepositoryPort.findAllByVoteId(voteId)
        afterCommit { notificationPort.notifyDrawResult(vote, result, participants.map { it.email }) }

        return result
    }

    fun reopen(
        voteId: UUID,
        requesterId: UUID,
    ) {
        val vote = getVoteOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        drawService.reopen(vote)
    }

    fun closeVote(
        voteId: UUID,
        requesterId: UUID,
    ) {
        val vote = getVoteOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        check(vote.status != VoteStatus.CLOSED) { "Vote is already closed" }
        vote.status = VoteStatus.CLOSED
        voteRepositoryPort.save(vote)
    }

    fun deleteVote(
        voteId: UUID,
        requesterId: UUID,
    ) {
        val vote = getVoteOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        voteRepositoryPort.delete(vote)
    }

    @Transactional(readOnly = true)
    fun getHistory(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): List<DrawHistoryDto> {
        val vote = getVoteOrThrow(voteId)
        checkCanView(vote, requesterId, requesterEmail)
        return drawHistoryRepositoryPort.findAllByVoteIdOrderByDrawnAtDesc(voteId).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getLastResult(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): DrawHistoryDto? {
        val vote = getVoteOrThrow(voteId)
        checkCanView(vote, requesterId, requesterEmail)
        return drawHistoryRepositoryPort.findTopByVoteIdOrderByDrawnAtDesc(voteId)?.toDto()
    }

    private fun getVoteOrThrow(voteId: UUID): Vote =
        voteRepositoryPort.findById(voteId) ?: throw NoSuchElementException("Vote not found")

    private fun checkIsCreator(
        vote: Vote,
        requesterId: UUID,
    ) {
        if (vote.creator.id != requesterId) {
            throw ForbiddenException("Only the creator can perform this action")
        }
    }

    private fun checkCanView(
        vote: Vote,
        requesterId: UUID,
        requesterEmail: String,
    ) {
        val hasAccess =
            vote.creator.id == requesterId ||
                participantRepositoryPort.existsByVoteIdAndEmail(vote.id, requesterEmail)
        if (!hasAccess) {
            throw ForbiddenException("Vote is not available for this user")
        }
    }

    private fun afterCommit(action: () -> Unit) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = action()
                }
            )
        } else {
            action()
        }
    }
}
