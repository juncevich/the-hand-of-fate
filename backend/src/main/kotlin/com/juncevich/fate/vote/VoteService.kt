package com.juncevich.fate.vote

import com.juncevich.fate.account.UserQueryService
import com.juncevich.fate.shared.*
import com.juncevich.fate.vote.domain.*
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.*

private const val MAX_HISTORY_PAGE_SIZE = 100

/**
 * The vote module's use cases. Each change loads the [Vote] aggregate under its row lock, lets it
 * check its rules and change itself, stores it and publishes the events it recorded ([store]);
 * metrics are counted here. Notifications and their email budget are the listeners' business
 * (`NotificationRecorder`).
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
    private val clock: Clock,
) {
    fun createVote(
        creatorId: UserId,
        request: CreateVoteCommand,
    ): VoteDetailView {
        val creator =
            userQueryService.findProfileById(creatorId)
                ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)
        val vote =
            Vote.create(
                creatorId = creatorId,
                creatorEmail = creator.email,
                title = request.title,
                description = request.description,
                mode = request.mode,
                drawTarget = request.drawTarget,
                participantEmails = request.participantEmails,
                optionTitles = request.options.orEmpty(),
                now = clock.instant()
            ) { emails ->
                userQueryService.findProfilesByEmailIn(emails).associate { it.email to it.displayName }
            }
        store(vote)
        meterRegistry.counter("vote.created", "mode", vote.mode.name).increment()
        return vote.toDetailView(lastResult = null, requesterId = creatorId)
    }

    @Transactional(readOnly = true)
    fun listVotes(
        userId: UserId,
        email: Email,
        pageable: Pageable,
    ): Page<VoteSummaryView> =
        votes
            .findSummaries(
                userId,
                email,
                pageable.toQuery()
            ).map { it.toView(isCreator = it.creatorId == userId) }
            .toPage()

    @Transactional(readOnly = true)
    fun getVote(
        voteId: VoteId,
        requesterId: UserId,
        requesterEmail: Email,
    ): VoteDetailView {
        val vote = findOrThrow(voteId)
        vote.requireViewer(requesterId, requesterEmail)
        return vote.toDetailView(history.findLatest(voteId), requesterId)
    }

    fun addParticipant(
        voteId: VoteId,
        requesterId: UserId,
        email: String,
    ) = change(voteId) { vote ->
        vote.invite(requesterId, email, clock.instant()) { userQueryService.findProfileByEmail(it)?.displayName }
    }

    fun removeParticipant(
        voteId: VoteId,
        requesterId: UserId,
        email: String,
    ) = change(voteId) { it.removeParticipant(requesterId, email) }

    fun addOption(
        voteId: VoteId,
        requesterId: UserId,
        title: String,
    ) = change(voteId) { it.addOption(requesterId, title, clock.instant()) }

    fun reorderOptions(
        voteId: VoteId,
        requesterId: UserId,
        orderedOptionIds: List<UUID>,
    ) = change(voteId) { it.reorderOptions(requesterId, orderedOptionIds) }

    fun removeOption(
        voteId: VoteId,
        requesterId: UserId,
        optionId: UUID,
    ) = change(voteId) { it.removeOption(requesterId, optionId) }

    fun draw(
        voteId: VoteId,
        requesterId: UserId,
    ): DrawResult {
        val vote = findForUpdateOrThrow(voteId)
        val outcome =
            vote.draw(
                requesterId,
                winnerPicker,
                clock.instant()
            ) { round -> history.findByRound(vote.id, round) }
        history.save(outcome.entry)
        store(vote)
        meterRegistry.counter("vote.draw.performed", "mode", vote.mode.name).increment()
        return outcome.toResult()
    }

    fun reopen(
        voteId: VoteId,
        requesterId: UserId,
    ) = change(voteId) { it.reopen(requesterId) }

    fun closeVote(
        voteId: VoteId,
        requesterId: UserId,
    ) = change(voteId) { it.close(requesterId) }

    fun deleteVote(
        voteId: VoteId,
        requesterId: UserId,
    ) {
        val vote = findForUpdateOrThrow(voteId)
        vote.requireCreator(requesterId)
        votes.delete(vote.id)
    }

    @Transactional(readOnly = true)
    fun getHistory(
        voteId: VoteId,
        requesterId: UserId,
        requesterEmail: Email,
    ): List<DrawHistoryView> =
        getHistory(voteId, requesterId, requesterEmail, PageRequest.of(0, MAX_HISTORY_PAGE_SIZE)).content

    @Transactional(readOnly = true)
    fun getHistory(
        voteId: VoteId,
        requesterId: UserId,
        requesterEmail: Email,
        pageable: Pageable,
    ): Page<DrawHistoryView> {
        findOrThrow(voteId).requireViewer(requesterId, requesterEmail)
        ensureValid(pageable.isPaged && pageable.pageSize <= MAX_HISTORY_PAGE_SIZE, ErrorCode.PAGE_INVALID) {
            "History page size must be between 1 and $MAX_HISTORY_PAGE_SIZE"
        }
        return history.findPage(voteId, pageable.toQuery()).map { it.toView() }.toPage()
    }

    @Transactional(readOnly = true)
    fun getLastResult(
        voteId: VoteId,
        requesterId: UserId,
        requesterEmail: Email,
    ): DrawHistoryView? {
        findOrThrow(voteId).requireViewer(requesterId, requesterEmail)
        return history.findLatest(voteId)?.toView()
    }

    /** Loads the vote under its lock, applies [action] and stores the result. */
    private fun change(
        voteId: VoteId,
        action: (Vote) -> Unit,
    ) {
        val vote = findForUpdateOrThrow(voteId)
        action(vote)
        store(vote)
    }

    /** Saves [vote], then publishes what it recorded — the listeners run in this transaction. */
    private fun store(vote: Vote) {
        votes.save(vote)
        vote.pullEvents().forEach(events::publishEvent)
    }

    private fun findForUpdateOrThrow(voteId: VoteId): Vote =
        votes.findForUpdate(voteId) ?: throw NotFoundException("Vote not found", ErrorCode.VOTE_NOT_FOUND)

    private fun findOrThrow(voteId: VoteId): Vote =
        votes.find(voteId) ?: throw NotFoundException("Vote not found", ErrorCode.VOTE_NOT_FOUND)
}

// ── Paging: Spring Data at the module API, plain types at the ports ─────────────

/** Sorting is not taken over: every listing has its own fixed order. */
private fun Pageable.toQuery() = PageQuery(pageNumber, pageSize)

private fun <T : Any> PageResult<T>.toPage(): Page<T> =
    PageImpl(content, PageRequest.of(query.number, query.size), totalElements)

// ── Domain → view mappings ──────────────────────────────────────────────────────

private fun VoteSummary.toView(isCreator: Boolean) =
    VoteSummaryView(
        id = id.value,
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
    requesterId: UserId,
) = VoteDetailView(
    id = id.value,
    title = title,
    description = description,
    mode = mode,
    drawTarget = drawTarget,
    status = status,
    currentRound = currentRound,
    participants = participants.map { ParticipantView(it.email.value, it.displayName) },
    options = options.map { VoteOptionView(it.id, it.title) },
    lastResult = lastResult?.toView(),
    isCreator = isCreator(requesterId),
    createdAt = createdAt
)

private fun DrawHistory.toView() = DrawHistoryView(id = id, winner = winner, round = round, drawnAt = drawnAt)

private fun DrawOutcome.toResult() =
    DrawResult(winner = entry.winner, round = entry.round, newRoundStarted = newRoundStarted)
