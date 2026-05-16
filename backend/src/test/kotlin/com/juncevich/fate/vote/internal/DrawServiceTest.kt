package com.juncevich.fate.vote

import com.juncevich.fate.auth.User
import com.juncevich.fate.vote.*
import com.juncevich.fate.vote.internal.persistence.DrawHistoryRepository
import com.juncevich.fate.vote.internal.persistence.VoteOptionRepository
import com.juncevich.fate.vote.internal.persistence.VoteParticipantRepository
import com.juncevich.fate.vote.internal.persistence.VoteRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DrawServiceTest {
    private val voteRepository = mockk<VoteRepository>()
    private val participantRepository = mockk<VoteParticipantRepository>()
    private val voteOptionRepository = mockk<VoteOptionRepository>()
    private val drawHistoryRepository = mockk<DrawHistoryRepository>()
    private val meterRegistry = mockk<MeterRegistry>()
    private val counter = mockk<Counter>(relaxed = true)

    private val drawService = DrawService(voteRepository, participantRepository, voteOptionRepository, drawHistoryRepository, meterRegistry)

    @BeforeEach
    fun setUp() {
        every { meterRegistry.counter(any<String>(), *anyVararg<String>()) } returns counter
    }

    private fun makeUser(email: String = "creator@test.com") = User(email = email, passwordHash = "hash", displayName = "Test User")

    private fun makeVote(
        mode: VoteMode = VoteMode.SIMPLE,
        status: VoteStatus = VoteStatus.PENDING,
    ) = Vote(title = "Test Vote", creator = makeUser(), mode = mode).also { it.status = status }

    private fun makeParticipant(
        vote: Vote,
        email: String,
        displayName: String? = null,
    ) = VoteParticipant(vote = vote, email = email, displayName = displayName)

    private fun makeHistory(
        vote: Vote,
        winnerEmail: String,
        round: Int = 1,
    ) = DrawHistory(vote = vote, winnerEmail = winnerEmail, winnerDisplayName = null, round = round)

    @Test
    fun `draw SIMPLE - picks the only participant as winner`() {
        val vote = makeVote(VoteMode.SIMPLE)
        val p1 = makeParticipant(vote, "winner@test.com", "Winner")
        val history = makeHistory(vote, p1.email)

        every { voteOptionRepository.findAllByVoteIdOrderByPositionAscCreatedAtAsc(vote.id) } returns emptyList()
        every { participantRepository.findAllByVoteId(vote.id) } returns listOf(p1)
        every { drawHistoryRepository.save(any()) } returns history
        every { voteRepository.save(any()) } returns vote

        val result = drawService.draw(vote)

        assertEquals(p1.email, result.winnerEmail)
        assertEquals(1, result.round)
        assertFalse(result.newRoundStarted)
        assertEquals(VoteStatus.DRAWN, vote.status)
        verify { drawHistoryRepository.save(any()) }
        verify { voteRepository.save(vote) }
    }

    @Test
    fun `draw SIMPLE - increments draw metric`() {
        val vote = makeVote(VoteMode.SIMPLE)
        val p1 = makeParticipant(vote, "a@a.com")

        every { voteOptionRepository.findAllByVoteIdOrderByPositionAscCreatedAtAsc(vote.id) } returns emptyList()
        every { participantRepository.findAllByVoteId(vote.id) } returns listOf(p1)
        every { drawHistoryRepository.save(any()) } returns makeHistory(vote, p1.email)
        every { voteRepository.save(any()) } returns vote

        drawService.draw(vote)

        verify { meterRegistry.counter("vote.draw.performed", "mode", "SIMPLE", "round", "1") }
        verify { counter.increment() }
    }

    @Test
    fun `draw - throws when vote is not PENDING`() {
        val vote = makeVote(status = VoteStatus.DRAWN)

        assertThrows<IllegalStateException> { drawService.draw(vote) }
    }

    @Test
    fun `draw - throws when vote has no participants`() {
        val vote = makeVote()
        every { voteOptionRepository.findAllByVoteIdOrderByPositionAscCreatedAtAsc(vote.id) } returns emptyList()
        every { participantRepository.findAllByVoteId(vote.id) } returns emptyList()

        val ex = assertThrows<IllegalStateException> { drawService.draw(vote) }
        assertTrue(ex.message!!.contains("no options or participants"))
    }

    @Test
    fun `draw FAIR_ROTATION - picks from eligible participants only`() {
        val vote = makeVote(VoteMode.FAIR_ROTATION)
        val p1 = makeParticipant(vote, "already-won@test.com")
        val p2 = makeParticipant(vote, "eligible@test.com")
        val history = makeHistory(vote, p2.email)

        every { voteOptionRepository.findAllByVoteIdOrderByPositionAscCreatedAtAsc(vote.id) } returns emptyList()
        every { participantRepository.findAllByVoteId(vote.id) } returns listOf(p1, p2)
        every { participantRepository.findEligibleEmailsForRound(vote.id, 1) } returns listOf(p2.email)
        every { drawHistoryRepository.save(any()) } returns history
        every { voteRepository.save(any()) } returns vote

        val result = drawService.draw(vote)

        assertEquals(p2.email, result.winnerEmail)
        assertFalse(result.newRoundStarted)
        assertEquals(1, result.round)
    }

    @Test
    fun `draw FAIR_ROTATION - starts new round when all participants have won`() {
        val vote = makeVote(VoteMode.FAIR_ROTATION)
        val p1 = makeParticipant(vote, "sole@test.com")
        val history = makeHistory(vote, p1.email, round = 2)

        every { voteOptionRepository.findAllByVoteIdOrderByPositionAscCreatedAtAsc(vote.id) } returns emptyList()
        every { participantRepository.findAllByVoteId(vote.id) } returns listOf(p1)
        every { participantRepository.findEligibleEmailsForRound(vote.id, 1) } returns emptyList()
        every { drawHistoryRepository.save(any()) } returns history
        every { voteRepository.save(any()) } returns vote

        val result = drawService.draw(vote)

        assertTrue(result.newRoundStarted)
        assertEquals(2, vote.currentRound)
    }

    @Test
    fun `reopen - changes status from DRAWN to PENDING`() {
        val vote = makeVote(status = VoteStatus.DRAWN)
        every { voteRepository.save(any()) } returns vote

        drawService.reopen(vote)

        assertEquals(VoteStatus.PENDING, vote.status)
        verify { voteRepository.save(vote) }
    }

    @Test
    fun `reopen - throws when vote is not DRAWN`() {
        val vote = makeVote(status = VoteStatus.PENDING)

        assertThrows<IllegalStateException> { drawService.reopen(vote) }
    }
}
