package com.juncevich.fate.vote

import com.juncevich.fate.auth.UserQueryService
import com.juncevich.fate.vote.internal.DrawService
import com.juncevich.fate.vote.internal.domain.DrawHistory
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.domain.VoteOption
import com.juncevich.fate.vote.internal.domain.VoteParticipant
import com.juncevich.fate.vote.internal.notification.NotificationService
import com.juncevich.fate.vote.internal.persistence.DrawHistoryRepository
import com.juncevich.fate.vote.internal.persistence.VoteOptionRepository
import com.juncevich.fate.vote.internal.persistence.VoteParticipantRepository
import com.juncevich.fate.vote.internal.persistence.VoteRepository
import com.juncevich.fate.vote.internal.persistence.toDetailDto
import com.juncevich.fate.vote.internal.persistence.toDto
import com.juncevich.fate.vote.internal.persistence.toSummaryDto
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class VoteService(
    private val voteRepository: VoteRepository,
    private val participantRepository: VoteParticipantRepository,
    private val voteOptionRepository: VoteOptionRepository,
    private val drawHistoryRepository: DrawHistoryRepository,
    private val userQueryService: UserQueryService,
    private val drawService: DrawService,
    private val notificationService: NotificationService,
    private val meterRegistry: MeterRegistry,
) {
    fun createVote(
        creatorId: UUID,
        request: CreateVoteCommand,
    ): VoteDetailDto {
        val creator = userQueryService.findById(creatorId) ?: throw NoSuchElementException("User not found")

        val vote =
            voteRepository.save(
                Vote(
                    title = request.title,
                    description = request.description,
                    creator = creator,
                    mode = request.mode
                )
            )

        val allEmails = setOf(creator.email) + request.participantEmails
        val existingUsers = userQueryService.findAllByEmailIn(allEmails).associateBy { it.email }
        val participants =
            participantRepository.saveAll(
                allEmails.map { email ->
                    VoteParticipant(
                        vote = vote,
                        email = email,
                        displayName = existingUsers[email]?.displayName
                    )
                }
            )

        val options =
            voteOptionRepository.saveAll(
                (request.options ?: emptyList())
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .mapIndexed { index, title -> VoteOption(vote = vote, title = title, position = index) }
            )

        meterRegistry.counter("vote.created", "mode", vote.mode.name).increment()

        request.participantEmails.forEach { email ->
            notificationService.notifyVoteInvitation(email, vote)
        }

        return vote.toDetailDto(participants, options, null, creator.id)
    }

    @Transactional(readOnly = true)
    fun listVotes(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<VoteSummaryDto> {
        val votes = voteRepository.findAllByUserIdOrParticipantEmail(userId, email, pageable)
        val voteIds = votes.content.map { it.id }
        val participantCounts =
            if (voteIds.isEmpty()) {
                emptyMap()
            } else {
                participantRepository
                    .countByVoteIds(voteIds)
                    .associate { it.voteId to it.participantCount }
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
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        checkCanView(vote, requesterId, requesterEmail)
        val participants = participantRepository.findAllByVoteId(voteId)
        val options = voteOptionRepository.findAllByVoteIdOrderByPositionAscCreatedAtAsc(voteId)
        val lastResult = drawHistoryRepository.findTopByVoteIdOrderByDrawnAtDesc(voteId)
        return vote.toDetailDto(participants, options, lastResult, requesterId)
    }

    fun addParticipant(
        voteId: UUID,
        requesterId: UUID,
        email: String,
    ) {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        check(vote.creator.id == requesterId) { "Only the creator can add participants" }
        check(vote.status == VoteStatus.PENDING) { "Cannot add participants to a non-pending vote" }
        check(!participantRepository.existsByVoteIdAndEmail(voteId, email)) { "Participant already exists" }

        val user = userQueryService.findByEmail(email)
        participantRepository.save(VoteParticipant(vote = vote, email = email, displayName = user?.displayName))

        notificationService.notifyVoteInvitation(email, vote)
    }

    fun removeParticipant(
        voteId: UUID,
        requesterId: UUID,
        email: String,
    ) {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        check(vote.creator.id == requesterId) { "Only the creator can remove participants" }
        check(vote.status == VoteStatus.PENDING) { "Cannot modify a non-pending vote" }
        participantRepository.deleteByVoteIdAndEmail(voteId, email)
    }

    fun addOption(
        voteId: UUID,
        requesterId: UUID,
        title: String,
    ) {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        check(vote.creator.id == requesterId) { "Only the creator can add options" }
        check(vote.status == VoteStatus.PENDING) { "Cannot add options to a non-pending vote" }
        val position = voteOptionRepository.countByVoteId(voteId).toInt()
        voteOptionRepository.save(VoteOption(vote = vote, title = title.trim(), position = position))
    }

    fun removeOption(
        voteId: UUID,
        requesterId: UUID,
        optionId: UUID,
    ) {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        check(vote.creator.id == requesterId) { "Only the creator can remove options" }
        check(vote.status == VoteStatus.PENDING) { "Cannot modify a non-pending vote" }
        voteOptionRepository.deleteByVoteIdAndId(voteId, optionId)
    }

    fun draw(
        voteId: UUID,
        requesterId: UUID,
    ): DrawResult {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        check(vote.creator.id == requesterId) { "Only the creator can perform a draw" }

        val result = drawService.draw(vote)

        val participants = participantRepository.findAllByVoteId(voteId)
        notificationService.notifyDrawResult(vote, result, participants.map { it.email })

        return result
    }

    fun reopen(
        voteId: UUID,
        requesterId: UUID,
    ) {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        check(vote.creator.id == requesterId) { "Only the creator can reopen a vote" }
        drawService.reopen(vote)
    }

    fun closeVote(
        voteId: UUID,
        requesterId: UUID,
    ) {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        check(vote.creator.id == requesterId) { "Only the creator can close a vote" }
        vote.status = VoteStatus.CLOSED
        voteRepository.save(vote)
    }

    fun deleteVote(
        voteId: UUID,
        requesterId: UUID,
    ) {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        check(vote.creator.id == requesterId) { "Only the creator can delete a vote" }
        voteRepository.delete(vote)
    }

    @Transactional(readOnly = true)
    fun getHistory(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): List<DrawHistoryDto> {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        checkCanView(vote, requesterId, requesterEmail)
        return drawHistoryRepository.findAllByVoteIdOrderByDrawnAtDesc(voteId).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getLastResult(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): DrawHistoryDto? {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        checkCanView(vote, requesterId, requesterEmail)
        return drawHistoryRepository.findTopByVoteIdOrderByDrawnAtDesc(voteId)?.toDto()
    }

    private fun checkCanView(
        vote: Vote,
        requesterId: UUID,
        requesterEmail: String,
    ) {
        val hasAccess =
            vote.creator.id == requesterId ||
                participantRepository.existsByVoteIdAndEmail(vote.id, requesterEmail)
        check(hasAccess) { "Vote is not available for this user" }
    }
}
