package com.juncevich.fate.vote

import com.juncevich.fate.account.UserQueryService
import com.juncevich.fate.notification.EmailBudget
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.ensureValid
import com.juncevich.fate.vote.domain.DrawHistory
import com.juncevich.fate.vote.domain.DrawHistoryRepository
import com.juncevich.fate.vote.domain.DrawOutcome
import com.juncevich.fate.vote.domain.ParticipantInvited
import com.juncevich.fate.vote.domain.Vote
import com.juncevich.fate.vote.domain.VoteDrawn
import com.juncevich.fate.vote.domain.VoteRepository
import com.juncevich.fate.vote.domain.VoteSummary
import com.juncevich.fate.vote.domain.WinnerPicker
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val MAX_HISTORY_PAGE_SIZE = 100

/**
 * The vote module's use cases. Each change loads the [Vote] aggregate under its row lock, lets it
 * check its rules and change itself, stores it, and then does what lies outside the aggregate:
 * the email budget, notification events and metrics.
 */
@Service
@Transactional
class VoteService(
    private val votes: VoteRepository,
    private val history: DrawHistoryRepository,
    private val userQueryService: UserQueryService,
    private val winnerPicker: WinnerPicker,
    private val events: ApplicationEventPublisher,
    private val meterRegistry: MeterRegistry,
    private val emailBudget: EmailBudget,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createVote(
        creatorId: UUID,
        request: CreateVoteCommand,
    ): VoteDetailView {
        val creator =
            userQueryService.findProfileById(creatorId)
                ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)
        val vote =
            Vote.create(
                creatorId = creator.id,
                creatorEmail = creator.email,
                title = request.title,
                description = request.description,
                mode = request.mode,
                drawTarget = request.drawTarget,
                participantEmails = request.participantEmails,
                optionTitles = request.options.orEmpty()
            ) { emails -> userQueryService.findProfilesByEmailIn(emails).associate { it.email to it.displayName } }
        val invitedEmails = vote.participants.map { it.email }.filter { it != creator.email }
        emailBudget.reserveOrThrow(creator.id, invitedEmails.size)

        votes.save(vote)
        meterRegistry.counter("vote.created", "mode", vote.mode.name).increment()
        invitedEmails.forEach { email ->
            events.publishEvent(ParticipantInvited(vote.id, vote.title, creator.displayName, email))
        }
        return vote.toDetailView(lastResult = null, requesterId = creator.id)
    }

    @Transactional(readOnly = true)
    fun listVotes(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<VoteSummaryView> =
        votes.findSummaries(userId, email, pageable).map {
            it.toView(
                isCreator =
                    it.creatorId == userId
            )
        }

    @Transactional(readOnly = true)
    fun getVote(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): VoteDetailView {
        val vote = findOrThrow(voteId)
        vote.requireViewer(requesterId, requesterEmail)
        return vote.toDetailView(history.findLatest(voteId), requesterId)
    }

    fun addParticipant(
        voteId: UUID,
        requesterId: UUID,
        email: String,
    ) {
        val vote = findForUpdateOrThrow(voteId)
        val participant = vote.invite(requesterId, email) { userQueryService.findProfileByEmail(it)?.displayName }
        emailBudget.reserveOrThrow(requesterId, 1)
        votes.save(vote)

        // Only the creator invites, so the requester's name is the creator's.
        val creator = checkNotNull(userQueryService.findProfileById(requesterId)) { "Creator $requesterId not found" }
        events.publishEvent(ParticipantInvited(vote.id, vote.title, creator.displayName, participant.email))
    }

    fun removeParticipant(
        voteId: UUID,
        requesterId: UUID,
        email: String,
    ) = change(voteId) { it.removeParticipant(requesterId, email) }

    fun addOption(
        voteId: UUID,
        requesterId: UUID,
        title: String,
    ) = change(voteId) { it.addOption(requesterId, title) }

    fun reorderOptions(
        voteId: UUID,
        requesterId: UUID,
        orderedOptionIds: List<UUID>,
    ) = change(voteId) { it.reorderOptions(requesterId, orderedOptionIds) }

    fun removeOption(
        voteId: UUID,
        requesterId: UUID,
        optionId: UUID,
    ) = change(voteId) { it.removeOption(requesterId, optionId) }

    fun draw(
        voteId: UUID,
        requesterId: UUID,
    ): DrawResult {
        val vote = findForUpdateOrThrow(voteId)
        val outcome = vote.draw(requesterId, winnerPicker) { round -> history.findByRound(vote.id, round) }
        history.save(outcome.entry)
        votes.save(vote)
        meterRegistry.counter("vote.draw.performed", "mode", vote.mode.name).increment()

        val result = outcome.toResult()
        val recipients = vote.participants.map { it.email }.distinct()
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
    ) = change(voteId) { it.reopen(requesterId) }

    fun closeVote(
        voteId: UUID,
        requesterId: UUID,
    ) = change(voteId) { it.close(requesterId) }

    fun deleteVote(
        voteId: UUID,
        requesterId: UUID,
    ) {
        val vote = findForUpdateOrThrow(voteId)
        vote.requireCreator(requesterId)
        votes.delete(vote.id)
    }

    @Transactional(readOnly = true)
    fun getHistory(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): List<DrawHistoryView> =
        getHistory(voteId, requesterId, requesterEmail, PageRequest.of(0, MAX_HISTORY_PAGE_SIZE)).content

    @Transactional(readOnly = true)
    fun getHistory(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
        pageable: Pageable,
    ): Page<DrawHistoryView> {
        findOrThrow(voteId).requireViewer(requesterId, requesterEmail)
        ensureValid(pageable.isPaged && pageable.pageSize <= MAX_HISTORY_PAGE_SIZE, ErrorCode.PAGE_INVALID) {
            "History page size must be between 1 and $MAX_HISTORY_PAGE_SIZE"
        }
        val boundedPage = PageRequest.of(pageable.pageNumber, pageable.pageSize)
        return history.findPage(voteId, boundedPage).map { it.toView() }
    }

    @Transactional(readOnly = true)
    fun getLastResult(
        voteId: UUID,
        requesterId: UUID,
        requesterEmail: String,
    ): DrawHistoryView? {
        findOrThrow(voteId).requireViewer(requesterId, requesterEmail)
        return history.findLatest(voteId)?.toView()
    }

    /** Loads the vote under its lock, applies [action] and stores the result. */
    private fun change(
        voteId: UUID,
        action: (Vote) -> Unit,
    ) {
        val vote = findForUpdateOrThrow(voteId)
        action(vote)
        votes.save(vote)
    }

    private fun findForUpdateOrThrow(voteId: UUID): Vote =
        votes.findForUpdate(voteId) ?: throw NotFoundException("Vote not found", ErrorCode.VOTE_NOT_FOUND)

    private fun findOrThrow(voteId: UUID): Vote =
        votes.find(voteId) ?: throw NotFoundException("Vote not found", ErrorCode.VOTE_NOT_FOUND)
}

// ── Domain → view mappings ──────────────────────────────────────────────────────

private fun VoteSummary.toView(isCreator: Boolean) =
    VoteSummaryView(
        id = id,
        title = title,
        mode = mode,
        status = status,
        currentRound = currentRound,
        participantCount = participantCount,
        isCreator = isCreator,
        createdAt = createdAt
    )

private fun Vote.toDetailView(
    lastResult: DrawHistory?,
    requesterId: UUID,
) = VoteDetailView(
    id = id,
    title = title,
    description = description,
    mode = mode,
    drawTarget = drawTarget,
    status = status,
    currentRound = currentRound,
    participants = participants.map { ParticipantView(it.email, it.displayName) },
    options = options.map { VoteOptionView(it.id, it.title) },
    lastResult = lastResult?.toView(),
    isCreator = isCreator(requesterId),
    createdAt = createdAt
)

private val DrawHistory.winner: Winner
    get() =
        when (this) {
            is DrawHistory.ParticipantWinner -> Winner.Participant(email, displayName)
            is DrawHistory.OptionWinner -> Winner.Option(optionTitle)
        }

private fun DrawHistory.toView() = DrawHistoryView(id = id, winner = winner, round = round, drawnAt = drawnAt)

private fun DrawOutcome.toResult() =
    DrawResult(winner = entry.winner, round = entry.round, newRoundStarted = newRoundStarted)
