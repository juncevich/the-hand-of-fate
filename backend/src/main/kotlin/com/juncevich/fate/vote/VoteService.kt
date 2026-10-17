package com.juncevich.fate.vote

import com.juncevich.fate.auth.UserQueryService
import com.juncevich.fate.notification.EmailBudget
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.ensureState
import com.juncevich.fate.shared.ensureValid
import com.juncevich.fate.shared.normalizeEmail
import com.juncevich.fate.shared.requireValidTitle
import com.juncevich.fate.vote.internal.DrawService
import com.juncevich.fate.vote.internal.ParticipantInvited
import com.juncevich.fate.vote.internal.VoteDrawn
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.domain.VoteOption
import com.juncevich.fate.vote.internal.domain.VoteParticipant
import com.juncevich.fate.vote.internal.port.DrawHistoryRepositoryPort
import com.juncevich.fate.vote.internal.port.ParticipantRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteOptionRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteRepositoryPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val events: ApplicationEventPublisher,
    private val meterRegistry: MeterRegistry,
    private val emailBudget: EmailBudget,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createVote(
        creatorId: UUID,
        request: CreateVoteCommand,
    ): VoteDetailView {
        val creator = userQueryService.findProfileById(creatorId) ?: throw NotFoundException("User not found")

        val title = requireValidTitle(request.title)
        ensureValid((request.description?.length ?: 0) <= VoteLimits.MAX_DESCRIPTION_LENGTH) {
            "Description must not exceed ${VoteLimits.MAX_DESCRIPTION_LENGTH} characters"
        }
        // Checked before normalizing so an oversized list is rejected without processing it.
        ensureValid(request.participantEmails.size <= VoteLimits.MAX_PARTICIPANTS) { participantLimitMessage() }
        ensureValid(request.options.orEmpty().size <= VoteLimits.MAX_OPTIONS) { optionLimitMessage() }
        val invitedEmails =
            request.participantEmails
                .map(::normalizeEmail)
                .distinct()
                .filter { it != creator.email }
        val allEmails = setOf(creator.email) + invitedEmails
        ensureValid(allEmails.size <= VoteLimits.MAX_PARTICIPANTS) { participantLimitMessage() }
        val optionTitles =
            request.options
                .orEmpty()
                .map(::requireValidTitle)
                .distinct()
        val drawTarget =
            request.drawTarget ?: if (optionTitles.isEmpty()) DrawTarget.PARTICIPANTS else DrawTarget.OPTIONS
        ensureValid(drawTarget == DrawTarget.OPTIONS || optionTitles.isEmpty()) { PARTICIPANTS_TARGET_OPTIONS_MESSAGE }
        emailBudget.reserveOrThrow(creator.id, invitedEmails.size)

        val vote =
            voteRepositoryPort.save(
                Vote(
                    title = title,
                    description = request.description,
                    creator = creator,
                    mode = request.mode,
                    drawTarget = drawTarget
                )
            )

        val existingUsers = userQueryService.findProfilesByEmailIn(allEmails).associateBy { it.email }
        val participants =
            participantRepositoryPort.saveAll(
                allEmails.map { email ->
                    VoteParticipant(voteId = vote.id, email = email, displayName = existingUsers[email]?.displayName)
                }
            )

        val options =
            voteOptionRepositoryPort.saveAll(
                optionTitles
                    .mapIndexed { index, title -> VoteOption(voteId = vote.id, title = title, position = index) }
            )

        meterRegistry.counter("vote.created", "mode", vote.mode.name).increment()

        invitedEmails.forEach { email -> events.publishEvent(vote.invitationFor(email)) }

        return vote.toDetailView(participants, options, null, creator.id)
    }

    @Transactional(readOnly = true)
    fun listVotes(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<VoteSummaryView> {
        val votes = voteRepositoryPort.findAllByUserIdOrParticipantEmail(userId, email, pageable)
        val voteIds = votes.content.map { it.id }
        val participantCounts =
            if (voteIds.isEmpty()) {
                emptyMap()
            } else {
                participantRepositoryPort.countByVoteIds(voteIds).associate { it.voteId to it.participantCount }
            }
        return votes.map { vote ->
            vote.toSummaryView(participantCounts[vote.id] ?: 0, vote.creator.id == userId)
        }
    }

    @Transactional(readOnly = true)
    fun getVote(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): VoteDetailView {
        val vote = getVoteOrThrow(voteId)
        checkCanView(vote, requesterId, requesterEmail)
        val participants = participantRepositoryPort.findAllByVoteId(voteId)
        val options = voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(voteId)
        val lastResult = drawHistoryRepositoryPort.findTopByVoteIdOrderByDrawnAtDescIdDesc(voteId)
        return vote.toDetailView(participants, options, lastResult, requesterId)
    }

    fun addParticipant(
        voteId: UUID,
        requesterId: UUID,
        email: String,
    ) {
        val vote = getVoteForUpdateOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        val normalizedEmail = normalizeEmail(email)
        ensureState(vote.status == VoteStatus.PENDING) { "Cannot add participants to a non-pending vote" }
        ensureState(
            !participantRepositoryPort.existsByVoteIdAndEmail(voteId, normalizedEmail)
        ) { "Participant already exists" }
        val participantCount = participantRepositoryPort.countByVoteIds(listOf(voteId)).sumOf { it.participantCount }
        ensureValid(participantCount < VoteLimits.MAX_PARTICIPANTS) { participantLimitMessage() }

        emailBudget.reserveOrThrow(requesterId, 1)
        val user = userQueryService.findProfileByEmail(normalizedEmail)
        participantRepositoryPort.save(
            VoteParticipant(voteId = voteId, email = normalizedEmail, displayName = user?.displayName)
        )

        events.publishEvent(vote.invitationFor(normalizedEmail))
    }

    fun removeParticipant(
        voteId: UUID,
        requesterId: UUID,
        email: String,
    ) {
        val vote = getVoteForUpdateOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        ensureState(vote.status == VoteStatus.PENDING) { "Cannot modify a non-pending vote" }
        participantRepositoryPort.deleteByVoteIdAndEmail(voteId, normalizeEmail(email))
    }

    fun addOption(
        voteId: UUID,
        requesterId: UUID,
        title: String,
    ) {
        val vote = getVoteForUpdateOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        ensureState(vote.status == VoteStatus.PENDING) { "Cannot add options to a non-pending vote" }
        ensureState(vote.drawTarget == DrawTarget.OPTIONS) { PARTICIPANTS_TARGET_OPTIONS_MESSAGE }
        val options = voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(voteId)
        ensureValid(options.size < VoteLimits.MAX_OPTIONS) { optionLimitMessage() }
        // Appended last; the vote lock keeps concurrent additions from taking the same position.
        val position = (options.maxOfOrNull { it.position } ?: -1) + 1
        voteOptionRepositoryPort.save(
            VoteOption(voteId = voteId, title = requireValidTitle(title), position = position)
        )
    }

    fun reorderOptions(
        voteId: UUID,
        requesterId: UUID,
        orderedOptionIds: List<UUID>,
    ) {
        val vote = getVoteForUpdateOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        ensureState(vote.status == VoteStatus.PENDING) { "Cannot modify a non-pending vote" }
        val currentIds = voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(voteId).map { it.id }
        ensureValid(orderedOptionIds.size == currentIds.size && orderedOptionIds.toSet() == currentIds.toSet()) {
            "The new order must list every option of the vote exactly once"
        }
        voteOptionRepositoryPort.updatePositions(voteId, orderedOptionIds)
    }

    fun removeOption(
        voteId: UUID,
        requesterId: UUID,
        optionId: UUID,
    ) {
        val vote = getVoteForUpdateOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        ensureState(vote.status == VoteStatus.PENDING) { "Cannot modify a non-pending vote" }
        voteOptionRepositoryPort.deleteByVoteIdAndId(voteId, optionId)
    }

    fun draw(
        voteId: UUID,
        requesterId: UUID,
    ): DrawResult {
        val vote = voteRepositoryPort.findByIdForUpdate(voteId) ?: throw NotFoundException("Vote not found")
        checkIsCreator(vote, requesterId)

        val result = drawService.draw(vote)

        val recipients = participantRepositoryPort.findAllByVoteId(voteId).map { it.email }.distinct()
        // The draw itself is never refused: over the budget only its result emails are dropped.
        if (emailBudget.tryReserve(requesterId, recipients.size)) {
            events.publishEvent(VoteDrawn(vote.id, vote.title, result, recipients))
        } else {
            log.warn("Result emails for draw of vote {} suppressed: email budget exhausted", vote.id)
        }

        return result
    }

    fun reopen(
        voteId: UUID,
        requesterId: UUID,
    ) {
        val vote = getVoteForUpdateOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        drawService.reopen(vote)
    }

    fun closeVote(
        voteId: UUID,
        requesterId: UUID,
    ) {
        val vote = getVoteForUpdateOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        ensureState(vote.status != VoteStatus.CLOSED) { "Vote is already closed" }
        vote.status = VoteStatus.CLOSED
        voteRepositoryPort.save(vote)
    }

    fun deleteVote(
        voteId: UUID,
        requesterId: UUID,
    ) {
        val vote = getVoteForUpdateOrThrow(voteId)
        checkIsCreator(vote, requesterId)
        voteRepositoryPort.delete(vote)
    }

    @Transactional(readOnly = true)
    fun getHistory(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): List<DrawHistoryView> = getHistory(voteId, requesterId, requesterEmail, PageRequest.of(0, 100)).content

    @Transactional(readOnly = true)
    fun getHistory(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
        pageable: Pageable,
    ): Page<DrawHistoryView> {
        val vote = getVoteOrThrow(voteId)
        checkCanView(vote, requesterId, requesterEmail)
        ensureValid(pageable.isPaged && pageable.pageSize <= 100) { "History page size must be between 1 and 100" }
        val boundedPage = PageRequest.of(pageable.pageNumber, pageable.pageSize)
        return drawHistoryRepositoryPort
            .findAllByVoteIdOrderByDrawnAtDescIdDesc(
                voteId,
                boundedPage
            ).map { it.toView() }
    }

    @Transactional(readOnly = true)
    fun getLastResult(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): DrawHistoryView? {
        val vote = getVoteOrThrow(voteId)
        checkCanView(vote, requesterId, requesterEmail)
        return drawHistoryRepositoryPort.findTopByVoteIdOrderByDrawnAtDescIdDesc(voteId)?.toView()
    }

    private fun getVoteForUpdateOrThrow(voteId: UUID): Vote =
        voteRepositoryPort.findByIdForUpdate(voteId) ?: throw NotFoundException("Vote not found")

    private fun getVoteOrThrow(voteId: UUID): Vote =
        voteRepositoryPort.findById(voteId) ?: throw NotFoundException("Vote not found")

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

    private fun Vote.invitationFor(email: String) = ParticipantInvited(id, title, creator.displayName, email)
}

private fun participantLimitMessage() = "A vote can have at most ${VoteLimits.MAX_PARTICIPANTS} participants"

private const val PARTICIPANTS_TARGET_OPTIONS_MESSAGE = "This vote draws from its participants and cannot have options"

private fun optionLimitMessage() = "A vote can have at most ${VoteLimits.MAX_OPTIONS} options"
