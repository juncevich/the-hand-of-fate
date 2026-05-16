package com.juncevich.fate.vote

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
    private val voteRepository: VoteRepository,
    private val participantRepository: VoteParticipantRepository,
    private val voteOptionRepository: VoteOptionRepository,
    private val drawHistoryRepository: DrawHistoryRepository,
    private val meterRegistry: MeterRegistry,
) {
    @Transactional
    fun draw(vote: Vote): DrawResult {
        check(vote.status == VoteStatus.PENDING) {
            "Vote must be in PENDING status to draw. Current status: ${vote.status}"
        }

        val options = voteOptionRepository.findAllByVoteIdOrderByPositionAscCreatedAtAsc(vote.id)
        val participants = if (options.isEmpty()) participantRepository.findAllByVoteId(vote.id) else emptyList()

        check(options.isNotEmpty() || participants.isNotEmpty()) {
            "Cannot draw: vote has no options or participants"
        }

        val (drawWinner, newRoundStarted) =
            if (options.isNotEmpty()) {
                when (vote.mode) {
                    VoteMode.SIMPLE -> DrawWinner.Option(options.random()) to false
                    VoteMode.FAIR_ROTATION -> drawFairRotationOption(vote, options)
                }
            } else {
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

        val history =
            when (drawWinner) {
                is DrawWinner.Participant -> {
                    drawHistoryRepository.save(
                        DrawHistory(
                            vote = vote,
                            winnerEmail = drawWinner.participant.email,
                            winnerDisplayName = drawWinner.participant.displayName,
                            round = vote.currentRound
                        )
                    )
                }

                is DrawWinner.Option -> {
                    drawHistoryRepository.save(
                        DrawHistory(
                            vote = vote,
                            winnerOption = drawWinner.option,
                            winnerOptionTitle = drawWinner.option.title,
                            round = vote.currentRound
                        )
                    )
                }
            }

        vote.status = VoteStatus.DRAWN
        voteRepository.save(vote)

        meterRegistry
            .counter(
                "vote.draw.performed",
                "mode",
                vote.mode.name,
                "round",
                vote.currentRound.toString()
            ).increment()

        return DrawResult(
            winnerEmail = history.winnerEmail,
            winnerDisplayName = history.winnerDisplayName,
            winnerOptionTitle = history.winnerOptionTitle,
            round = history.round,
            newRoundStarted = newRoundStarted
        )
    }

    @Transactional
    fun reopen(vote: Vote) {
        check(vote.status == VoteStatus.DRAWN) { "Only DRAWN votes can be reopened" }
        vote.status = VoteStatus.PENDING
        voteRepository.save(vote)
    }

    private fun drawFairRotation(
        vote: Vote,
        participants: List<VoteParticipant>,
    ): Pair<VoteParticipant, Boolean> {
        var newRoundStarted = false

        var eligibleEmails = participantRepository.findEligibleEmailsForRound(vote.id, vote.currentRound)

        if (eligibleEmails.isEmpty()) {
            vote.currentRound++
            voteRepository.save(vote)
            eligibleEmails = participants.map { it.email }
            newRoundStarted = true
        }

        val winnerEmail = eligibleEmails.random()
        val winner = participants.first { it.email == winnerEmail }

        return winner to newRoundStarted
    }

    private fun drawFairRotationOption(
        vote: Vote,
        options: List<VoteOption>,
    ): Pair<DrawWinner.Option, Boolean> {
        var newRoundStarted = false

        var eligibleOptions = voteOptionRepository.findEligibleOptionsForRound(vote.id, vote.currentRound)

        if (eligibleOptions.isEmpty()) {
            vote.currentRound++
            voteRepository.save(vote)
            eligibleOptions = options
            newRoundStarted = true
        }

        return DrawWinner.Option(eligibleOptions.random()) to newRoundStarted
    }
}
