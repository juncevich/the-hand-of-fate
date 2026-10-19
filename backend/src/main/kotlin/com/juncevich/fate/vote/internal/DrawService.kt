package com.juncevich.fate.vote.internal

import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.ensureState
import com.juncevich.fate.vote.DrawResult
import com.juncevich.fate.vote.DrawTarget
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import com.juncevich.fate.vote.internal.domain.DrawHistory
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.domain.VoteOption
import com.juncevich.fate.vote.internal.domain.VoteParticipant
import com.juncevich.fate.vote.internal.port.DrawHistoryRepositoryPort
import com.juncevich.fate.vote.internal.port.ParticipantRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteOptionRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteRepositoryPort
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private sealed interface DrawWinner {
    data class Participant(
        val participant: VoteParticipant,
    ) : DrawWinner

    data class Option(
        val option: VoteOption,
    ) : DrawWinner
}

@Service
class DrawService(
    private val voteRepositoryPort: VoteRepositoryPort,
    private val participantRepositoryPort: ParticipantRepositoryPort,
    private val voteOptionRepositoryPort: VoteOptionRepositoryPort,
    private val drawHistoryRepositoryPort: DrawHistoryRepositoryPort,
    private val meterRegistry: MeterRegistry,
    private val winnerPicker: WinnerPicker,
) {
    @Transactional
    fun draw(vote: Vote): DrawResult {
        ensureState(vote.status == VoteStatus.PENDING, ErrorCode.VOTE_NOT_PENDING) {
            "Vote must be in PENDING status to draw. Current status: ${vote.status}"
        }

        val options =
            when (vote.drawTarget) {
                DrawTarget.OPTIONS -> voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id)
                DrawTarget.PARTICIPANTS -> emptyList()
            }
        val participants =
            when (vote.drawTarget) {
                DrawTarget.OPTIONS -> emptyList()
                DrawTarget.PARTICIPANTS -> participantRepositoryPort.findAllByVoteId(vote.id)
            }
        when (vote.drawTarget) {
            DrawTarget.OPTIONS -> {
                ensureState(options.isNotEmpty(), ErrorCode.NO_OPTIONS) { "Cannot draw: add at least one option first" }
            }

            DrawTarget.PARTICIPANTS -> {
                ensureState(
                    participants.isNotEmpty(),
                    ErrorCode.NO_PARTICIPANTS
                ) { "Cannot draw: vote has no participants" }
            }
        }

        val (drawWinner, newRoundStarted, audit) = selectWinner(vote, options, participants)

        val history =
            when (drawWinner) {
                is DrawWinner.Participant -> {
                    drawHistoryRepositoryPort.save(
                        DrawHistory.ParticipantWinner(
                            voteId = vote.id,
                            email = drawWinner.participant.email,
                            displayName = drawWinner.participant.displayName,
                            round = vote.currentRound,
                            audit = audit
                        )
                    )
                }

                is DrawWinner.Option -> {
                    drawHistoryRepositoryPort.save(
                        DrawHistory.OptionWinner(
                            voteId = vote.id,
                            optionId = drawWinner.option.id,
                            optionTitle = drawWinner.option.title,
                            round = vote.currentRound,
                            audit = audit
                        )
                    )
                }
            }

        vote.status = VoteStatus.DRAWN
        voteRepositoryPort.save(vote)

        meterRegistry
            .counter(
                "vote.draw.performed",
                "mode",
                vote.mode.name
            ).increment()

        return when (history) {
            is DrawHistory.ParticipantWinner -> {
                DrawResult(
                    winnerEmail = history.email,
                    winnerDisplayName = history.displayName,
                    winnerOptionTitle = null,
                    round = history.round,
                    newRoundStarted = newRoundStarted
                )
            }

            is DrawHistory.OptionWinner -> {
                DrawResult(
                    winnerEmail = null,
                    winnerDisplayName = null,
                    winnerOptionTitle = history.optionTitle,
                    round = history.round,
                    newRoundStarted = newRoundStarted
                )
            }
        }
    }

    @Transactional
    fun reopen(vote: Vote) {
        ensureState(vote.status == VoteStatus.DRAWN, ErrorCode.VOTE_NOT_DRAWN) { "Only DRAWN votes can be reopened" }
        vote.status = VoteStatus.PENDING
        voteRepositoryPort.save(vote)
    }

    private fun selectWinner(
        vote: Vote,
        options: List<VoteOption>,
        participants: List<VoteParticipant>,
    ): Selection =
        if (options.isNotEmpty()) {
            val (eligible, newRoundStarted) =
                when (vote.mode) {
                    VoteMode.SIMPLE -> {
                        options to false
                    }

                    VoteMode.FAIR_ROTATION -> {
                        eligibleThisRound(vote, options) {
                            voteOptionRepositoryPort.findEligibleOptionsForRound(vote.id, vote.currentRound)
                        }
                    }
                }
            // Ids, not titles: an option can be deleted and a new one added under the same title.
            val byKey = eligible.associateBy { it.id.toString() }
            val (key, audit) = winnerPicker.pick(byKey.keys)
            Selection(DrawWinner.Option(byKey.getValue(key)), newRoundStarted, audit)
        } else {
            val (eligible, newRoundStarted) =
                when (vote.mode) {
                    VoteMode.SIMPLE -> {
                        participants to false
                    }

                    VoteMode.FAIR_ROTATION -> {
                        eligibleThisRound(vote, participants) {
                            val emails =
                                participantRepositoryPort
                                    .findEligibleEmailsForRound(
                                        vote.id,
                                        vote.currentRound
                                    ).toSet()
                            participants.filter { it.email in emails }
                        }
                    }
                }
            val byKey = eligible.associateBy { it.email }
            val (key, audit) = winnerPicker.pick(byKey.keys)
            Selection(DrawWinner.Participant(byKey.getValue(key)), newRoundStarted, audit)
        }

    /** FAIR_ROTATION: who has not won this round yet; once everyone has, a new round starts with all. */
    private fun <T> eligibleThisRound(
        vote: Vote,
        all: List<T>,
        notYetWon: () -> List<T>,
    ): Pair<List<T>, Boolean> {
        val eligible = notYetWon()
        if (eligible.isNotEmpty()) return eligible to false
        vote.currentRound++
        return all to true
    }

    private data class Selection(
        val winner: DrawWinner,
        val newRoundStarted: Boolean,
        val audit: DrawAudit,
    )
}
