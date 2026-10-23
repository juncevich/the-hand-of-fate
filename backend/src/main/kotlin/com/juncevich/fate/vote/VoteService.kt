package com.juncevich.fate.vote

import com.juncevich.fate.account.UserQueryService
import com.juncevich.fate.account.mailbox
import com.juncevich.fate.notification.EmailBudget
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.shared.ensureValid
import com.juncevich.fate.vote.domain.DrawHistory
import com.juncevich.fate.vote.domain.DrawHistoryRepository
import com.juncevich.fate.vote.domain.DrawOutcome
import com.juncevich.fate.vote.domain.PageQuery
import com.juncevich.fate.vote.domain.PageResult
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
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
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createVote(
        creatorId: UserId,
        request: CreateVoteCommand,
    ): VoteDetailView {
        val creator =
            userQueryService.findProfileById(creatorId.value)
                ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)
        val creatorEmail = creator.mailbox
        val vote =
            Vote.create(
                creatorId = creatorId,
                creatorEmail = creatorEmail,
                title = request.title,
                description = request.description,
                mode = request.mode,
                drawTarget = request.drawTarget,
                participantEmails = request.participantEmails,
                optionTitles = request.options.orEmpty(),
                now = clock.instant()
            ) { emails ->
                userQueryService
                    .findProfilesByEmailIn(emails.map { it.value })
                    .associate { it.mailbox to it.displayName }
            }
        val invitedEmails = vote.participants.map { it.email }.filter { it != creatorEmail }
        emailBudget.reserveOrThrow(creatorId.value, invitedEmails.size)

        votes.save(vote)
        meterRegistry.counter("vote.created", "mode", vote.mode.name).increment()
        invitedEmails.forEach { email ->
            events.publishEvent(ParticipantInvited(vote.id, vote.title, creator.displayName, email))
        }
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
    ) {
        val vote = findForUpdateOrThrow(voteId)
        val participant =
            vote.invite(requesterId, email, clock.instant()) {
                userQueryService.findProfileByEmail(it.value)?.displayName
            }
        emailBudget.reserveOrThrow(requesterId.value, 1)
        votes.save(vote)

        // Only the creator invites, so the requester's name is the creator's.
        val creator =
            checkNotNull(userQueryService.findProfileById(requesterId.value)) { "Creator $requesterId not found" }
        events.publishEvent(ParticipantInvited(vote.id, vote.title, creator.displayName, participant.email))
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
        votes.save(vote)
        meterRegistry.counter("vote.draw.performed", "mode", vote.mode.name).increment()

        val result = outcome.toResult()
        val recipients = vote.participants.map { it.email }.distinct()
        // The draw itself is never refused: over the budget only its result emails are dropped.
        if (emailBudget.tryReserve(requesterId.value, recipients.size)) {
            events.publishEvent(VoteDrawn(vote.id, vote.title, result, recipients))
        } else {
            log.warn("Result emails for draw of vote {} suppressed: email budget exhausted", vote.id)
        }
        return result
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
        votes.save(vote)
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

private val DrawHistory.winner: Winner
    get() =
        when (this) {
            is DrawHistory.ParticipantWinner -> Winner.Participant(email?.value, displayName)
            is DrawHistory.OptionWinner -> Winner.Option(optionTitle)
        }

private fun DrawHistory.toView() = DrawHistoryView(id = id, winner = winner, round = round, drawnAt = drawnAt)

private fun DrawOutcome.toResult() =
    DrawResult(winner = entry.winner, round = entry.round, newRoundStarted = newRoundStarted)
