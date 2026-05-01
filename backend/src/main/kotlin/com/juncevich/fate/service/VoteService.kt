package com.juncevich.fate.service

import com.juncevich.fate.domain.user.UserRepository
import com.juncevich.fate.domain.vote.*
import com.juncevich.fate.web.vote.CreateVoteRequest
import com.juncevich.fate.web.vote.VoteDetailDto
import com.juncevich.fate.web.vote.VoteSummaryDto
import com.juncevich.fate.web.vote.toDetailDto
import com.juncevich.fate.web.vote.toSummaryDto
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
    private val userRepository: UserRepository,
    private val drawService: DrawService,
    private val notificationService: NotificationService,
    private val meterRegistry: MeterRegistry,
) {

    fun createVote(creatorId: UUID, request: CreateVoteRequest): VoteDetailDto {
        val creator = userRepository.findById(creatorId).orElseThrow()

        val vote = voteRepository.save(
            Vote(
                title = request.title,
                description = request.description,
                creator = creator,
                mode = request.mode,
            )
        )

        val allEmails = (setOf(creator.email) + request.participantEmails).distinct()
        val participants = allEmails.map { email ->
            val user = userRepository.findByEmail(email)
            participantRepository.save(
                VoteParticipant(
                    vote = vote,
                    email = email,
                    displayName = user?.displayName,
                )
            )
        }

        val options = (request.options ?: emptyList())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .mapIndexed { index, title ->
                voteOptionRepository.save(VoteOption(vote = vote, title = title, position = index))
            }

        meterRegistry.counter("vote.created", "mode", vote.mode.name).increment()

        request.participantEmails.forEach { email ->
            notificationService.notifyVoteInvitation(email, vote)
        }

        return vote.toDetailDto(participants, options, null)
    }

    @Transactional(readOnly = true)
    fun listVotes(userId: UUID, email: String, pageable: Pageable): Page<VoteSummaryDto> =
        voteRepository.findAllByUserIdOrParticipantEmail(userId, email, pageable)
            .map { vote ->
                val participantCount = participantRepository.countByVoteId(vote.id)
                vote.toSummaryDto(participantCount, vote.creator.id == userId)
            }

    @Transactional(readOnly = true)
    fun getVote(voteId: UUID, requesterId: UUID): VoteDetailDto {
        val vote = voteRepository.findById(voteId).orElseThrow { NoSuchElementException("Vote not found") }
        val participants = participantRepository.findAllByVoteId(voteId)
        val options = voteOptionRepository.findAllByVoteIdOrderByPositionAscCreatedAtAsc(voteId)
        val lastResult = drawHistoryRepository.findTopByVoteIdOrderByDrawnAtDesc(voteId)
        return vote.toDetailDto(participants, options, lastResult, requesterId)
    }

    fun addParticipant(voteId: UUID, requesterId: UUID, email: String) {
        val vote = voteRepository.findById(voteId).orElseThrow()
        check(vote.creator.id == requesterId) { "Only the creator can add participants" }
        check(vote.status == VoteStatus.PENDING) { "Cannot add participants to a non-pending vote" }
        check(!participantRepository.existsByVoteIdAndEmail(voteId, email)) { "Participant already exists" }

        val user = userRepository.findByEmail(email)
        participantRepository.save(VoteParticipant(vote = vote, email = email, displayName = user?.displayName))

        notificationService.notifyVoteInvitation(email, vote)
    }

    fun removeParticipant(voteId: UUID, requesterId: UUID, email: String) {
        val vote = voteRepository.findById(voteId).orElseThrow()
        check(vote.creator.id == requesterId) { "Only the creator can remove participants" }
        check(vote.status == VoteStatus.PENDING) { "Cannot modify a non-pending vote" }
        participantRepository.deleteByVoteIdAndEmail(voteId, email)
    }

    fun addOption(voteId: UUID, requesterId: UUID, title: String) {
        val vote = voteRepository.findById(voteId).orElseThrow()
        check(vote.creator.id == requesterId) { "Only the creator can add options" }
        check(vote.status == VoteStatus.PENDING) { "Cannot add options to a non-pending vote" }
        val position = voteOptionRepository.countByVoteId(voteId).toInt()
        voteOptionRepository.save(VoteOption(vote = vote, title = title.trim(), position = position))
    }

    fun removeOption(voteId: UUID, requesterId: UUID, optionId: UUID) {
        val vote = voteRepository.findById(voteId).orElseThrow()
        check(vote.creator.id == requesterId) { "Only the creator can remove options" }
        check(vote.status == VoteStatus.PENDING) { "Cannot modify a non-pending vote" }
        voteOptionRepository.deleteById(optionId)
    }

    fun draw(voteId: UUID, requesterId: UUID): DrawResult {
        val vote = voteRepository.findById(voteId).orElseThrow()
        check(vote.creator.id == requesterId) { "Only the creator can perform a draw" }

        val result = drawService.draw(voteId)

        val participants = participantRepository.findAllByVoteId(voteId)
        notificationService.notifyDrawResult(vote, result, participants.map { it.email })

        return result
    }

    fun reopen(voteId: UUID, requesterId: UUID) {
        val vote = voteRepository.findById(voteId).orElseThrow()
        check(vote.creator.id == requesterId) { "Only the creator can reopen a vote" }
        drawService.reopen(voteId)
    }

    fun closeVote(voteId: UUID, requesterId: UUID) {
        val vote = voteRepository.findById(voteId).orElseThrow()
        check(vote.creator.id == requesterId) { "Only the creator can close a vote" }
        vote.status = VoteStatus.CLOSED
        voteRepository.save(vote)
    }

    fun deleteVote(voteId: UUID, requesterId: UUID) {
        val vote = voteRepository.findById(voteId).orElseThrow()
        check(vote.creator.id == requesterId) { "Only the creator can delete a vote" }
        voteRepository.delete(vote)
    }

    @Transactional(readOnly = true)
    fun getHistory(voteId: UUID): List<DrawHistory> =
        drawHistoryRepository.findAllByVoteIdOrderByDrawnAtDesc(voteId)
}
