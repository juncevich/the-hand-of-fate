package com.juncevich.fate.vote.domain

import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.shared.ensureState
import com.juncevich.fate.shared.ensureValid
import com.juncevich.fate.shared.requireValidTitle
import com.juncevich.fate.shared.uuidV7
import com.juncevich.fate.vote.DrawTarget
import com.juncevich.fate.vote.VoteId
import com.juncevich.fate.vote.VoteLimits
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import java.time.Instant
import java.util.UUID

data class Participant(
    val email: Email,
    val displayName: String? = null,
    val id: UUID = uuidV7(),
    val addedAt: Instant,
)

data class VoteOption(
    val title: String,
    val position: Int,
    val id: UUID = uuidV7(),
    val createdAt: Instant,
)

/** What [Vote.draw] produced: the history entry to record and whether the draw started a new round. */
data class DrawOutcome(
    val entry: DrawHistory,
    val newRoundStarted: Boolean,
)

/**
 * A vote with its participants and options — the aggregate every vote rule is checked on.
 * Changes go through its methods; [VoteRepository] loads and stores it as a whole, under the
 * vote's row lock for any change.
 *
 * The public constructor restores a stored vote as it is; a new one comes from [create].
 * The aggregate never reads the clock: a change that records a time takes it as `now`.
 * What happened is recorded as [VoteEvent]s, which the caller takes with [pullEvents] after saving.
 */
@Suppress("LongParameterList") // the restoring constructor takes the whole stored state
class Vote(
    val id: VoteId,
    val title: String,
    val description: String?,
    val creatorId: UserId,
    val mode: VoteMode,
    val drawTarget: DrawTarget,
    status: VoteStatus,
    currentRound: Int,
    val createdAt: Instant,
    /** The stored version this copy was loaded at; a save from an older copy is rejected. */
    val version: Int,
    participants: List<Participant>,
    options: List<VoteOption>,
) {
    var status: VoteStatus = status
        private set

    var currentRound: Int = currentRound
        private set

    private val _participants = participants.toMutableList()
    private val _options = options.sortedWith(compareBy({ it.position }, { it.createdAt })).toMutableList()

    private val recordedEvents = mutableListOf<VoteEvent>()

    val participants: List<Participant> get() = _participants.toList()

    /** In display order. */
    val options: List<VoteOption> get() = _options.toList()

    /** The events recorded since the last call, oldest first; publish them once the vote is saved. */
    fun pullEvents(): List<VoteEvent> = recordedEvents.toList().also { recordedEvents.clear() }

    fun isCreator(userId: UserId) = creatorId == userId

    fun requireCreator(userId: UserId) {
        if (!isCreator(userId)) {
            throw ForbiddenException("Only the creator can perform this action", ErrorCode.NOT_VOTE_CREATOR)
        }
    }

    /** The creator and every participant (also the observers of an options vote) may see the vote. */
    fun requireViewer(
        userId: UserId,
        email: Email,
    ) {
        if (!isCreator(userId) && _participants.none { it.email == email }) {
            throw ForbiddenException("Vote is not available for this user", ErrorCode.VOTE_ACCESS_DENIED)
        }
    }

    /** Adds [email] as a participant; [displayNameOf] looks up the name of an existing account. */
    fun invite(
        by: UserId,
        email: String,
        now: Instant,
        displayNameOf: (Email) -> String?,
    ): Participant {
        requireCreator(by)
        val normalizedEmail = Email.of(email)
        ensureState(status == VoteStatus.PENDING, ErrorCode.VOTE_NOT_PENDING) {
            "Cannot add participants to a non-pending vote"
        }
        ensureState(_participants.none { it.email == normalizedEmail }, ErrorCode.PARTICIPANT_EXISTS) {
            "Participant already exists"
        }
        ensureValid(_participants.size < VoteLimits.MAX_PARTICIPANTS, ErrorCode.TOO_MANY_PARTICIPANTS) {
            participantLimitMessage()
        }
        val participant = Participant(normalizedEmail, displayNameOf(normalizedEmail), addedAt = now)
        _participants += participant
        recordedEvents += ParticipantInvited(id, title, by, participant.email)
        return participant
    }

    /**
     * Removes [email] from the participants; an address that is not one is ignored. The creator may
     * remove themselves — a vote run for a group they are not part of — and still manages and sees it.
     */
    fun removeParticipant(
        by: UserId,
        email: String,
    ) {
        requireCreator(by)
        requirePendingForChange()
        val normalizedEmail = Email.of(email)
        _participants.removeIf { it.email == normalizedEmail }
    }

    fun addOption(
        by: UserId,
        title: String,
        now: Instant,
    ): VoteOption {
        requireCreator(by)
        ensureState(status == VoteStatus.PENDING, ErrorCode.VOTE_NOT_PENDING) {
            "Cannot add options to a non-pending vote"
        }
        ensureState(drawTarget == DrawTarget.OPTIONS, ErrorCode.OPTIONS_NOT_ALLOWED) {
            PARTICIPANTS_TARGET_OPTIONS_MESSAGE
        }
        ensureValid(_options.size < VoteLimits.MAX_OPTIONS, ErrorCode.TOO_MANY_OPTIONS) { optionLimitMessage() }
        val validTitle = requireValidTitle(title)
        if (_options.any { it.title == validTitle }) {
            throw ConflictException("An option with this title already exists", ErrorCode.ALREADY_EXISTS)
        }
        // Appended last, after any gap left by a removal.
        val position = (_options.maxOfOrNull { it.position } ?: -1) + 1
        return VoteOption(validTitle, position, createdAt = now).also(_options::add)
    }

    /** [orderedIds] must list every option exactly once; each option's position becomes its index. */
    fun reorderOptions(
        by: UserId,
        orderedIds: List<UUID>,
    ) {
        requireCreator(by)
        requirePendingForChange()
        val currentIds = _options.map { it.id }
        ensureValid(
            orderedIds.size == currentIds.size && orderedIds.toSet() == currentIds.toSet(),
            ErrorCode.OPTION_ORDER_INVALID
        ) {
            "The new order must list every option of the vote exactly once"
        }
        val byId = _options.associateBy { it.id }
        _options.clear()
        orderedIds.forEachIndexed { position, id -> _options += byId.getValue(id).copy(position = position) }
    }

    fun removeOption(
        by: UserId,
        optionId: UUID,
    ) {
        requireCreator(by)
        requirePendingForChange()
        _options.removeIf { it.id == optionId }
    }

    /**
     * Picks a winner from the [drawTarget] pool with [picker]. FAIR_ROTATION leaves out those who
     * already won the current round — read with [roundHistory] — and starts a new round with
     * everyone once all have won. The vote becomes DRAWN.
     */
    fun draw(
        by: UserId,
        picker: WinnerPicker,
        now: Instant,
        roundHistory: (round: Int) -> List<DrawHistory>,
    ): DrawOutcome {
        requireCreator(by)
        ensureState(status == VoteStatus.PENDING, ErrorCode.VOTE_NOT_PENDING) {
            "Vote must be in PENDING status to draw. Current status: $status"
        }
        val outcome =
            when (drawTarget) {
                DrawTarget.OPTIONS -> drawOption(picker, now, roundHistory)
                DrawTarget.PARTICIPANTS -> drawParticipant(picker, now, roundHistory)
            }
        status = VoteStatus.DRAWN
        recordedEvents += VoteDrawn(id, title, by, outcome, _participants.map { it.email })
        return outcome
    }

    fun reopen(by: UserId) {
        requireCreator(by)
        ensureState(status == VoteStatus.DRAWN, ErrorCode.VOTE_NOT_DRAWN) { "Only DRAWN votes can be reopened" }
        status = VoteStatus.PENDING
    }

    fun close(by: UserId) {
        requireCreator(by)
        ensureState(status != VoteStatus.CLOSED, ErrorCode.VOTE_ALREADY_CLOSED) { "Vote is already closed" }
        status = VoteStatus.CLOSED
    }

    private fun drawOption(
        picker: WinnerPicker,
        now: Instant,
        roundHistory: (Int) -> List<DrawHistory>,
    ): DrawOutcome {
        ensureState(_options.isNotEmpty(), ErrorCode.NO_OPTIONS) { "Cannot draw: add at least one option first" }
        // Ids, not titles: an option can be deleted and a new one added under the same title.
        val eligible =
            eligibleThisRound(_options, { it.id.toString() }, roundHistory) {
                (it as? DrawHistory.OptionWinner)?.optionId?.toString()
            }
        val byKey = eligible.pool.associateBy { it.id.toString() }
        val (key, audit) = picker.pick(byKey.keys)
        val option = byKey.getValue(key)
        val entry =
            DrawHistory.OptionWinner(
                voteId = id,
                optionId = option.id,
                optionTitle = option.title,
                round = currentRound,
                drawnAt = now,
                audit = audit
            )
        return DrawOutcome(entry, eligible.newRoundStarted)
    }

    private fun drawParticipant(
        picker: WinnerPicker,
        now: Instant,
        roundHistory: (Int) -> List<DrawHistory>,
    ): DrawOutcome {
        ensureState(_participants.isNotEmpty(), ErrorCode.NO_PARTICIPANTS) { "Cannot draw: vote has no participants" }
        val eligible =
            eligibleThisRound(_participants, { it.email.value }, roundHistory) {
                (it as? DrawHistory.ParticipantWinner)?.email?.value
            }
        val byKey = eligible.pool.associateBy { it.email.value }
        val (key, audit) = picker.pick(byKey.keys)
        val participant = byKey.getValue(key)
        val entry =
            DrawHistory.ParticipantWinner(
                voteId = id,
                email = participant.email,
                displayName = participant.displayName,
                round = currentRound,
                drawnAt = now,
                audit = audit
            )
        return DrawOutcome(entry, eligible.newRoundStarted)
    }

    /** FAIR_ROTATION: who has not won this round yet; once everyone has, a new round starts with all. */
    private fun <T> eligibleThisRound(
        pool: List<T>,
        key: (T) -> String,
        roundHistory: (Int) -> List<DrawHistory>,
        winnerKey: (DrawHistory) -> String?,
    ): Eligible<T> {
        if (mode == VoteMode.SIMPLE) return Eligible(pool, newRoundStarted = false)
        val won = roundHistory(currentRound).mapNotNullTo(HashSet(), winnerKey)
        val notYetWon = pool.filter { key(it) !in won }
        if (notYetWon.isNotEmpty()) return Eligible(notYetWon, newRoundStarted = false)
        currentRound++
        return Eligible(pool, newRoundStarted = true)
    }

    /** Who may win this draw, and whether choosing them started a new round. */
    private data class Eligible<T>(
        val pool: List<T>,
        val newRoundStarted: Boolean,
    )

    private fun requirePendingForChange() =
        ensureState(status == VoteStatus.PENDING, ErrorCode.VOTE_NOT_PENDING) { "Cannot modify a non-pending vote" }

    companion object {
        /**
         * A new PENDING vote: the creator becomes a participant along with the invited [participantEmails]
         * (normalized, duplicates dropped); [displayNamesOf] maps the addresses of existing accounts to
         * their names. Without [drawTarget] the vote draws from its options if any are given.
         * [now] becomes its creation time.
         */
        @Suppress("LongParameterList")
        fun create(
            creatorId: UserId,
            creatorEmail: Email,
            title: String,
            description: String?,
            mode: VoteMode,
            drawTarget: DrawTarget?,
            participantEmails: List<String>,
            optionTitles: List<String>,
            now: Instant,
            displayNamesOf: (Set<Email>) -> Map<Email, String>,
        ): Vote {
            val validTitle = requireValidTitle(title)
            ensureValid(
                (description?.length ?: 0) <= VoteLimits.MAX_DESCRIPTION_LENGTH,
                ErrorCode.DESCRIPTION_TOO_LONG
            ) {
                "Description must not exceed ${VoteLimits.MAX_DESCRIPTION_LENGTH} characters"
            }
            // Checked before normalizing so an oversized list is rejected without processing it.
            ensureValid(participantEmails.size <= VoteLimits.MAX_PARTICIPANTS, ErrorCode.TOO_MANY_PARTICIPANTS) {
                participantLimitMessage()
            }
            ensureValid(optionTitles.size <= VoteLimits.MAX_OPTIONS, ErrorCode.TOO_MANY_OPTIONS) {
                optionLimitMessage()
            }
            val invitedEmails =
                participantEmails
                    .map(Email::of)
                    .distinct()
                    .filter { it != creatorEmail }
            val allEmails = setOf(creatorEmail) + invitedEmails
            ensureValid(allEmails.size <= VoteLimits.MAX_PARTICIPANTS, ErrorCode.TOO_MANY_PARTICIPANTS) {
                participantLimitMessage()
            }
            val validOptionTitles = optionTitles.map(::requireValidTitle).distinct()
            val target = drawTarget ?: if (validOptionTitles.isEmpty()) DrawTarget.PARTICIPANTS else DrawTarget.OPTIONS
            ensureValid(target == DrawTarget.OPTIONS || validOptionTitles.isEmpty(), ErrorCode.OPTIONS_NOT_ALLOWED) {
                PARTICIPANTS_TARGET_OPTIONS_MESSAGE
            }
            val displayNames = displayNamesOf(allEmails)
            val vote =
                Vote(
                    id = VoteId.new(),
                    title = validTitle,
                    description = description,
                    creatorId = creatorId,
                    mode = mode,
                    drawTarget = target,
                    status = VoteStatus.PENDING,
                    currentRound = 1,
                    createdAt = now,
                    version = 0,
                    participants = allEmails.map { Participant(it, displayNames[it], addedAt = now) },
                    options =
                        validOptionTitles.mapIndexed { position, optionTitle ->
                            VoteOption(optionTitle, position, createdAt = now)
                        }
                )
            vote.recordedEvents += VoteCreated(vote.id, vote.title, creatorId, invitedEmails)
            return vote
        }
    }
}

private fun participantLimitMessage() = "A vote can have at most ${VoteLimits.MAX_PARTICIPANTS} participants"

private fun optionLimitMessage() = "A vote can have at most ${VoteLimits.MAX_OPTIONS} options"

private const val PARTICIPANTS_TARGET_OPTIONS_MESSAGE = "This vote draws from its participants and cannot have options"
