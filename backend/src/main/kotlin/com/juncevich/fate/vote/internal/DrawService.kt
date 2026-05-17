package com.juncevich.fate.vote.internal

import com.juncevich.fate.vote.DrawResult
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

private sealed class DrawWinner {
    data class Participant(
        val participant: VoteParticipant,
    ) : DrawWinner()

    data class Option(
        val option: VoteOption,
    ) : DrawWinner()
}

@Service
class DrawService(
    private val voteRepositoryPort: VoteRepositoryPort,
    private val participantRepositoryPort: ParticipantRepositoryPort,
    private val voteOptionRepositoryPort: VoteOptionRepositoryPort,
    private val drawHistoryRepositoryPort: DrawHistoryRepositoryPort,
    private val meterRegistry: MeterRegistry,
) {
    @Transactional
    fun draw(vote: Vote): DrawResult {
        check(vote.status == VoteStatus.PENDING) {
            "Vote must be in PENDING status to draw. Current status: ${vote.status}"
        }

        val options = voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id)
        val participants = if (options.isEmpty()) participantRepositoryPort.findAllByVoteId(vote.id) else emptyList()

        check(options.isNotEmpty() || participants.isNotEmpty()) {
            "Cannot draw: vote has no options or participants"
        }

        val (drawWinner, newRoundStarted) = selectWinner(vote, options, participants)

        val history =
            when (drawWinner) {
                is DrawWinner.Participant -> {
                    drawHistoryRepositoryPort.save(
                        DrawHistory.ParticipantWinner(
                            voteId = vote.id,
                            email = drawWinner.participant.email,
                            displayName = drawWinner.participant.displayName,
                            round = vote.currentRound
                        )
                    )
                }

                is DrawWinner.Option -> {
                    drawHistoryRepositoryPort.save(
                        DrawHistory.OptionWinner(
                            voteId = vote.id,
                            optionId = drawWinner.option.id,
                            optionTitle = drawWinner.option.title,
                            round = vote.currentRound
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
                vote.mode.name,
                "round",
                vote.currentRound.toString()
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
        check(vote.status == VoteStatus.DRAWN) { "Only DRAWN votes can be reopened" }
        vote.status = VoteStatus.PENDING
        voteRepositoryPort.save(vote)
    }

    private fun selectWinner(
        vote: Vote,
        options: List<VoteOption>,
        participants: List<VoteParticipant>,
    ): Pair<DrawWinner, Boolean> =
        when {
            options.isNotEmpty() -> {
                when (vote.mode) {
                    VoteMode.SIMPLE -> DrawWinner.Option(options.random()) to false
                    VoteMode.FAIR_ROTATION -> drawFairRotationOption(vote, options)
                }
            }

            else -> {
                when (vote.mode) {
                    VoteMode.SIMPLE -> {
                        DrawWinner.Participant(participants.random()) to false
                    }

                    VoteMode.FAIR_ROTATION -> {
                        val (winner, newRound) = drawFairRotation(vote, participants)
                        DrawWinner.Participant(winner) to newRound
                    }
                }
            }
        }

    private fun drawFairRotation(
        vote: Vote,
        participants: List<VoteParticipant>,
    ): Pair<VoteParticipant, Boolean> {
        var eligibleEmails = participantRepositoryPort.findEligibleEmailsForRound(vote.id, vote.currentRound)
        val newRoundStarted = eligibleEmails.isEmpty()

        if (newRoundStarted) {
            vote.currentRound++
            eligibleEmails = participants.map { it.email }
        }

        val winnerEmail = eligibleEmails.random()
        val winner = participants.first { it.email == winnerEmail }
        return winner to newRoundStarted
    }

    private fun drawFairRotationOption(
        vote: Vote,
        options: List<VoteOption>,
    ): Pair<DrawWinner.Option, Boolean> {
        var eligibleOptions = voteOptionRepositoryPort.findEligibleOptionsForRound(vote.id, vote.currentRound)
        val newRoundStarted = eligibleOptions.isEmpty()

        if (newRoundStarted) {
            vote.currentRound++
            eligibleOptions = options
        }

        return DrawWinner.Option(eligibleOptions.random()) to newRoundStarted
    }
}
