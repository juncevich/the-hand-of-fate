package com.juncevich.fate.service

import com.juncevich.fate.domain.vote.*
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class DrawResult(
    val winnerEmail: String,
    val winnerDisplayName: String?,
    val round: Int,
    val newRoundStarted: Boolean,
)

@Service
class DrawService(
    private val voteRepository: VoteRepository,
    private val participantRepository: VoteParticipantRepository,
    private val drawHistoryRepository: DrawHistoryRepository,
    private val meterRegistry: MeterRegistry,
) {

    @Transactional
    fun draw(voteId: java.util.UUID): DrawResult {
        val vote = voteRepository.findById(voteId).orElseThrow { IllegalArgumentException("Vote not found") }

        check(vote.status == VoteStatus.PENDING) {
            "Vote must be in PENDING status to draw. Current status: ${vote.status}"
        }

        val participants = participantRepository.findAllByVoteId(voteId)
        check(participants.isNotEmpty()) { "Cannot draw: vote has no participants" }

        val (winner, newRoundStarted) = when (vote.mode) {
            VoteMode.SIMPLE -> drawSimple(participants) to false
            VoteMode.FAIR_ROTATION -> drawFairRotation(vote, participants)
        }

        val history = drawHistoryRepository.save(
            DrawHistory(
                vote = vote,
                winnerEmail = winner.email,
                winnerDisplayName = winner.displayName,
                round = vote.currentRound,
            )
        )

        vote.status = VoteStatus.DRAWN
        voteRepository.save(vote)

        meterRegistry.counter("vote.draw.performed",
            "mode", vote.mode.name,
            "round", vote.currentRound.toString()
        ).increment()

        return DrawResult(
            winnerEmail = history.winnerEmail,
            winnerDisplayName = history.winnerDisplayName,
            round = history.round,
            newRoundStarted = newRoundStarted,
        )
    }

    fun reopen(voteId: java.util.UUID) {
        val vote = voteRepository.findById(voteId).orElseThrow { IllegalArgumentException("Vote not found") }
        check(vote.status == VoteStatus.DRAWN) { "Only DRAWN votes can be reopened" }
        vote.status = VoteStatus.PENDING
        voteRepository.save(vote)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun drawSimple(participants: List<VoteParticipant>): VoteParticipant =
        participants.random()

    private fun drawFairRotation(
        vote: Vote,
        participants: List<VoteParticipant>,
    ): Pair<VoteParticipant, Boolean> {
        var newRoundStarted = false

        var eligibleEmails = participantRepository
            .findEligibleEmailsForRound(vote.id, vote.currentRound)

        if (eligibleEmails.isEmpty()) {
            // Everyone has won this round — start a new round
            vote.currentRound++
            voteRepository.save(vote)
            eligibleEmails = participants.map { it.email }
            newRoundStarted = true
        }

        val winnerEmail = eligibleEmails.random()
        val winner = participants.first { it.email == winnerEmail }

        return winner to newRoundStarted
    }
}
