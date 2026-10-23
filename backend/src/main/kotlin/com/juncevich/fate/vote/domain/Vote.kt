package com.juncevich.fate.vote.domain

import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.ensureState
import com.juncevich.fate.shared.ensureValid
import com.juncevich.fate.shared.normalizeEmail
import com.juncevich.fate.shared.requireValidTitle
import com.juncevich.fate.shared.uuidV7
import com.juncevich.fate.vote.DrawTarget
import com.juncevich.fate.vote.VoteLimits
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import java.time.Instant
import java.util.UUID

data class Participant(
    val email: String,
    val displayName: String? = null,
    val id: UUID = uuidV7(),
    val addedAt: Instant = Instant.now(),
)

data class VoteOption(
    val title: String,
    val position: Int,
    val id: UUID = uuidV7(),
    val createdAt: Instant = Instant.now(),
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
 */
class Vote(
    val id: UUID,
    val title: String,
    val description: String?,
    val creatorId: UUID,
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

    val participants: List<Participant> get() = _participants.toList()

    /** In display order. */
    val options: List<VoteOption> get() = _options.toList()

    fun isCreator(userId: UUID) = creatorId == userId

    fun requireCreator(userId: UUID) {
        if (!isCreator(userId)) {
            throw ForbiddenException("Only the creator can perform this action", ErrorCode.NOT_VOTE_CREATOR)
        }
    }

    /** The creator and every participant (also the observers of an options vote) may see the vote. */
    fun requireViewer(
        userId: UUID,
        email: String,
    ) {
        if (!isCreator(userId) && _participants.none { it.email == email }) {
            throw ForbiddenException("Vote is not available for this user", ErrorCode.VOTE_ACCESS_DENIED)
        }
    }

    /** Adds [email] as a participant; [displayNameOf] looks up the name of an existing account. */
    fun invite(
        by: UUID,
        email: String,
        displayNameOf: (String) -> String?,
    ): Participant {
        requireCreator(by)
        val normalizedEmail = normalizeEmail(email)
        ensureState(status == VoteStatus.PENDING, ErrorCode.VOTE_NOT_PENDING) {
            "Cannot add participants to a non-pending vote"
        }
        ensureState(_participants.none { it.email == normalizedEmail }, ErrorCode.PARTICIPANT_EXISTS) {
            "Participant already exists"
        }
        ensureValid(_participants.size < VoteLimits.MAX_PARTICIPANTS, ErrorCode.TOO_MANY_PARTICIPANTS) {
            participantLimitMessage()
        }
        return Participant(normalizedEmail, displayNameOf(normalizedEmail)).also(_participants::add)
    }

    fun removeParticipant(
        by: UUID,
        email: String,
    ) {
        requireCreator(by)
        requirePendingForChange()
        val normalizedEmail = normalizeEmail(email)
        _participants.removeIf { it.email == normalizedEmail }
    }

    fun addOption(
        by: UUID,
        title: String,
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
        return VoteOption(validTitle, position).also(_options::add)
    }

    /** [orderedIds] must list every option exactly once; each option's position becomes its index. */
    fun reorderOptions(
        by: UUID,
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
        by: UUID,
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
        by: UUID,
        picker: WinnerPicker,
        roundHistory: (round: Int) -> List<DrawHistory>,
    ): DrawOutcome {
        requireCreator(by)
        ensureState(status == VoteStatus.PENDING, ErrorCode.VOTE_NOT_PENDING) {
            "Vote must be in PENDING status to draw. Current status: $status"
        }
        val outcome =
            when (drawTarget) {
                DrawTarget.OPTIONS -> drawOption(picker, roundHistory)
                DrawTarget.PARTICIPANTS -> drawParticipant(picker, roundHistory)
            }
        status = VoteStatus.DRAWN
        return outcome
    }

    fun reopen(by: UUID) {
        requireCreator(by)
        ensureState(status == VoteStatus.DRAWN, ErrorCode.VOTE_NOT_DRAWN) { "Only DRAWN votes can be reopened" }
        status = VoteStatus.PENDING
    }

    fun close(by: UUID) {
        requireCreator(by)
        ensureState(status != VoteStatus.CLOSED, ErrorCode.VOTE_ALREADY_CLOSED) { "Vote is already closed" }
        status = VoteStatus.CLOSED
    }

    private fun drawOption(
        picker: WinnerPicker,
        roundHistory: (Int) -> List<DrawHistory>,
    ): DrawOutcome {
        ensureState(_options.isNotEmpty(), ErrorCode.NO_OPTIONS) { "Cannot draw: add at least one option first" }
        // Ids, not titles: an option can be deleted and a new one added under the same title.
        val (eligible, newRoundStarted) =
            eligibleThisRound(_options, { it.id.toString() }, roundHistory) {
                (it as? DrawHistory.OptionWinner)?.optionId?.toString()
            }
        val byKey = eligible.associateBy { it.id.toString() }
        val (key, audit) = picker.pick(byKey.keys)
        val option = byKey.getValue(key)
        val entry =
            DrawHistory.OptionWinner(
                voteId = id,
                optionId = option.id,
                optionTitle = option.title,
                round = currentRound,
                audit = audit
            )
        return DrawOutcome(entry, newRoundStarted)
    }

    private fun drawParticipant(
        picker: WinnerPicker,
        roundHistory: (Int) -> List<DrawHistory>,
    ): DrawOutcome {
        ensureState(_participants.isNotEmpty(), ErrorCode.NO_PARTICIPANTS) { "Cannot draw: vote has no participants" }
        val (eligible, newRoundStarted) =
            eligibleThisRound(_participants, { it.email }, roundHistory) {
                (it as? DrawHistory.ParticipantWinner)?.email
            }
        val byKey = eligible.associateBy { it.email }
        val (key, audit) = picker.pick(byKey.keys)
        val participant = byKey.getValue(key)
        val entry =
            DrawHistory.ParticipantWinner(
                voteId = id,
                email = participant.email,
                displayName = participant.displayName,
                round = currentRound,
                audit = audit
            )
        return DrawOutcome(entry, newRoundStarted)
    }

    /** FAIR_ROTATION: who has not won this round yet; once everyone has, a new round starts with all. */
    private fun <T> eligibleThisRound(
        pool: List<T>,
        key: (T) -> String,
        roundHistory: (Int) -> List<DrawHistory>,
        winnerKey: (DrawHistory) -> String?,
    ): Pair<List<T>, Boolean> {
        if (mode == VoteMode.SIMPLE) return pool to false
        val won = roundHistory(currentRound).mapNotNullTo(HashSet(), winnerKey)
        val notYetWon = pool.filter { key(it) !in won }
        if (notYetWon.isNotEmpty()) return notYetWon to false
        currentRound++
        return pool to true
    }

    private fun requirePendingForChange() =
        ensureState(status == VoteStatus.PENDING, ErrorCode.VOTE_NOT_PENDING) { "Cannot modify a non-pending vote" }

    companion object {
        /**
         * A new PENDING vote: the creator becomes a participant along with the invited [participantEmails]
         * (normalized, duplicates dropped); [displayNamesOf] maps the addresses of existing accounts to
         * their names. Without [drawTarget] the vote draws from its options if any are given.
         */
        @Suppress("LongParameterList")
        fun create(
            creatorId: UUID,
            creatorEmail: String,
            title: String,
            description: String?,
            mode: VoteMode,
            drawTarget: DrawTarget?,
            participantEmails: List<String>,
            optionTitles: List<String>,
            displayNamesOf: (Set<String>) -> Map<String, String>,
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
                    .map(::normalizeEmail)
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
            return Vote(
                id = uuidV7(),
                title = validTitle,
                description = description,
                creatorId = creatorId,
                mode = mode,
                drawTarget = target,
                status = VoteStatus.PENDING,
                currentRound = 1,
                createdAt = Instant.now(),
                version = 0,
                participants = allEmails.map { Participant(it, displayNames[it]) },
                options = validOptionTitles.mapIndexed { position, optionTitle -> VoteOption(optionTitle, position) }
            )
        }
    }
}

private fun participantLimitMessage() = "A vote can have at most ${VoteLimits.MAX_PARTICIPANTS} participants"

private fun optionLimitMessage() = "A vote can have at most ${VoteLimits.MAX_OPTIONS} options"

private const val PARTICIPANTS_TARGET_OPTIONS_MESSAGE = "This vote draws from its participants and cannot have options"
