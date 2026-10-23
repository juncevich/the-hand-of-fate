package com.juncevich.fate.vote.domain

import com.juncevich.fate.account.User
import com.juncevich.fate.account.toProfile
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.vote.*
import com.juncevich.fate.vote.port.DrawHistoryRepositoryPort
import com.juncevich.fate.vote.port.ParticipantRepositoryPort
import com.juncevich.fate.vote.port.VoteOptionRepositoryPort
import com.juncevich.fate.vote.port.VoteRepositoryPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DrawServiceTest {
    private val voteRepositoryPort = mockk<VoteRepositoryPort>()
    private val participantRepositoryPort = mockk<ParticipantRepositoryPort>()
    private val voteOptionRepositoryPort = mockk<VoteOptionRepositoryPort>()
    private val drawHistoryRepositoryPort = mockk<DrawHistoryRepositoryPort>()
    private val meterRegistry = mockk<MeterRegistry>()
    private val counter = mockk<Counter>(relaxed = true)

    private val drawService =
        DrawService(
            voteRepositoryPort,
            participantRepositoryPort,
            voteOptionRepositoryPort,
            drawHistoryRepositoryPort,
            meterRegistry,
            WinnerPicker()
        )

    @BeforeEach
    fun setUp() {
        every { meterRegistry.counter(any<String>(), *anyVararg<String>()) } returns counter
    }

    private fun makeUser(email: String = "creator@test.com") = User(email = email, displayName = "Test User")

    private fun makeVote(
        mode: VoteMode = VoteMode.SIMPLE,
        status: VoteStatus = VoteStatus.PENDING,
        drawTarget: DrawTarget = DrawTarget.PARTICIPANTS,
    ) = Vote(title = "Test Vote", creator = makeUser().toProfile(), mode = mode, drawTarget = drawTarget)
        .also { it.status = status }

    private fun makeParticipant(
        vote: Vote,
        email: String,
        displayName: String? = null,
    ) = VoteParticipant(voteId = vote.id, email = email, displayName = displayName)

    private fun makeHistory(
        vote: Vote,
        winnerEmail: String,
        round: Int = 1,
    ) = DrawHistory.ParticipantWinner(voteId = vote.id, email = winnerEmail, round = round)

    private fun makeOption(
        vote: Vote,
        title: String,
    ) = VoteOption(voteId = vote.id, title = title)

    private fun makeOptionHistory(
        vote: Vote,
        option: VoteOption,
        round: Int = 1,
    ) = DrawHistory.OptionWinner(voteId = vote.id, optionId = option.id, optionTitle = option.title, round = round)

    @Test
    fun `draw SIMPLE - picks the only participant as winner`() {
        val vote = makeVote(VoteMode.SIMPLE)
        val p1 = makeParticipant(vote, "winner@test.com", "Winner")
        val history = makeHistory(vote, p1.email)

        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns emptyList()
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns listOf(p1)
        every { drawHistoryRepositoryPort.save(any()) } returns history
        every { voteRepositoryPort.save(any()) } returns vote

        val result = drawService.draw(vote)

        assertEquals(p1.email, result.winnerEmail)
        assertEquals(1, result.round)
        assertFalse(result.newRoundStarted)
        assertEquals(VoteStatus.DRAWN, vote.status)
        verify { drawHistoryRepositoryPort.save(any()) }
        verify { voteRepositoryPort.save(vote) }
    }

    @Test
    fun `draw SIMPLE - increments draw metric`() {
        val vote = makeVote(VoteMode.SIMPLE)
        val p1 = makeParticipant(vote, "a@a.com")

        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns emptyList()
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns listOf(p1)
        every { drawHistoryRepositoryPort.save(any()) } returns makeHistory(vote, p1.email)
        every { voteRepositoryPort.save(any()) } returns vote

        drawService.draw(vote)

        verify { meterRegistry.counter("vote.draw.performed", "mode", "SIMPLE") }
        verify { counter.increment() }
    }

    @Test
    fun `draw - throws when vote is not PENDING`() {
        val vote = makeVote(status = VoteStatus.DRAWN)

        assertThrows<ConflictException> { drawService.draw(vote) }
    }

    @Test
    fun `draw - throws when vote has no participants`() {
        val vote = makeVote()
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns emptyList()

        val ex = assertThrows<ConflictException> { drawService.draw(vote) }
        assertTrue(ex.message!!.contains("no participants"))
    }

    @Test
    fun `draw - an options vote without options asks for one instead of drawing participants`() {
        val vote = makeVote(drawTarget = DrawTarget.OPTIONS)
        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns emptyList()

        val ex = assertThrows<ConflictException> { drawService.draw(vote) }
        assertTrue(ex.message!!.contains("add at least one option"))
        verify(exactly = 0) { participantRepositoryPort.findAllByVoteId(any()) }
    }

    @Test
    fun `draw FAIR_ROTATION - picks from eligible participants only`() {
        val vote = makeVote(VoteMode.FAIR_ROTATION)
        val p1 = makeParticipant(vote, "already-won@test.com")
        val p2 = makeParticipant(vote, "eligible@test.com")
        val history = makeHistory(vote, p2.email)

        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns emptyList()
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns listOf(p1, p2)
        every { participantRepositoryPort.findEligibleEmailsForRound(vote.id, 1) } returns listOf(p2.email)
        every { drawHistoryRepositoryPort.save(any()) } returns history
        every { voteRepositoryPort.save(any()) } returns vote

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

        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns emptyList()
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns listOf(p1)
        every { participantRepositoryPort.findEligibleEmailsForRound(vote.id, 1) } returns emptyList()
        every { drawHistoryRepositoryPort.save(any()) } returns history
        every { voteRepositoryPort.save(any()) } returns vote

        val result = drawService.draw(vote)

        assertTrue(result.newRoundStarted)
        assertEquals(2, vote.currentRound)
    }

    @Test
    fun `draw FAIR_ROTATION option-mode - picks from eligible options only`() {
        val vote = makeVote(VoteMode.FAIR_ROTATION, drawTarget = DrawTarget.OPTIONS)
        val o1 = makeOption(vote, "already-won")
        val o2 = makeOption(vote, "eligible")
        val history = makeOptionHistory(vote, o2)

        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns listOf(o1, o2)
        every { voteOptionRepositoryPort.findEligibleOptionsForRound(vote.id, 1) } returns listOf(o2)
        every { drawHistoryRepositoryPort.save(any()) } returns history
        every { voteRepositoryPort.save(any()) } returns vote

        val result = drawService.draw(vote)

        assertEquals(o2.title, result.winnerOptionTitle)
        assertNull(result.winnerEmail)
        assertFalse(result.newRoundStarted)
        assertEquals(1, result.round)
        verify(exactly = 0) { participantRepositoryPort.findAllByVoteId(any()) }
    }

    @Test
    fun `draw FAIR_ROTATION option-mode - starts new round when all options have won`() {
        val vote = makeVote(VoteMode.FAIR_ROTATION, drawTarget = DrawTarget.OPTIONS)
        val o1 = makeOption(vote, "sole")
        val history = makeOptionHistory(vote, o1, round = 2)

        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns listOf(o1)
        every { voteOptionRepositoryPort.findEligibleOptionsForRound(vote.id, 1) } returns emptyList()
        every { drawHistoryRepositoryPort.save(any()) } returns history
        every { voteRepositoryPort.save(any()) } returns vote

        val result = drawService.draw(vote)

        assertTrue(result.newRoundStarted)
        assertEquals(2, vote.currentRound)
        assertEquals(o1.title, result.winnerOptionTitle)
    }

    @Test
    fun `draw - an options vote draws options and leaves participants out`() {
        val vote = makeVote(VoteMode.SIMPLE, drawTarget = DrawTarget.OPTIONS)
        val option = makeOption(vote, "only-option")
        val history = makeOptionHistory(vote, option)

        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns listOf(option)
        every { drawHistoryRepositoryPort.save(any()) } returns history
        every { voteRepositoryPort.save(any()) } returns vote

        val result = drawService.draw(vote)

        assertEquals(option.title, result.winnerOptionTitle)
        assertNull(result.winnerEmail)
        verify(exactly = 0) { participantRepositoryPort.findAllByVoteId(any()) }
    }

    @Test
    fun `draw - a participants vote never looks at options`() {
        val vote = makeVote(VoteMode.SIMPLE)
        val participant = makeParticipant(vote, "p@test.com")
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns listOf(participant)
        every { drawHistoryRepositoryPort.save(any()) } returns makeHistory(vote, participant.email)
        every { voteRepositoryPort.save(any()) } returns vote

        assertEquals("p@test.com", drawService.draw(vote).winnerEmail)
        verify(exactly = 0) { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(any()) }
    }

    @Test
    fun `reopen - changes status from DRAWN to PENDING`() {
        val vote = makeVote(status = VoteStatus.DRAWN)
        every { voteRepositoryPort.save(any()) } returns vote

        drawService.reopen(vote)

        assertEquals(VoteStatus.PENDING, vote.status)
        verify { voteRepositoryPort.save(vote) }
    }

    @Test
    fun `reopen - throws when vote is not DRAWN`() {
        val vote = makeVote(status = VoteStatus.PENDING)

        assertThrows<ConflictException> { drawService.reopen(vote) }
    }

    @Test
    fun `draw metric does not create new time series for each round`() {
        val registry =
            io.micrometer.core.instrument.simple
                .SimpleMeterRegistry()
        val service = DrawService(voteRepositoryPort, participantRepositoryPort, voteOptionRepositoryPort, drawHistoryRepositoryPort, registry, WinnerPicker())
        val vote = makeVote(VoteMode.SIMPLE)
        val participant = makeParticipant(vote, "a@test.com")
        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns emptyList()
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns listOf(participant)
        every { drawHistoryRepositoryPort.save(any()) } answers { firstArg() }
        every { voteRepositoryPort.save(any()) } returns vote
        service.draw(vote)
        vote.status = VoteStatus.PENDING
        vote.currentRound = 1000
        service.draw(vote)
        assertEquals(1, registry.meters.size)
        assertEquals(2.0, registry.get("vote.draw.performed").counter().count())
        assertNull(
            registry.meters
                .single()
                .id
                .getTag("round")
        )
        registry.close()
    }

    @Test
    fun `draw records an audit that reproduces the winner`() {
        val vote = makeVote(VoteMode.SIMPLE)
        val participants = listOf("c@test.com", "a@test.com", "b@test.com").map { makeParticipant(vote, it) }
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns participants
        every { drawHistoryRepositoryPort.save(any()) } answers { firstArg() }
        every { voteRepositoryPort.save(any()) } returns vote

        val result = drawService.draw(vote)

        verify {
            drawHistoryRepositoryPort.save(
                match {
                    val audit = checkNotNull(it.audit)
                    audit.candidates == listOf("a@test.com", "b@test.com", "c@test.com") &&
                        audit.candidates[WinnerPicker.index(audit.seed, 3)] == result.winnerEmail
                }
            )
        }
    }

    @Test
    fun `FAIR_ROTATION audit lists only the candidates still eligible this round`() {
        val vote = makeVote(VoteMode.FAIR_ROTATION, drawTarget = DrawTarget.OPTIONS)
        val options = List(3) { makeOption(vote, "Option $it") }
        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns options
        every { voteOptionRepositoryPort.findEligibleOptionsForRound(vote.id, 1) } returns options.drop(1)
        every { drawHistoryRepositoryPort.save(any()) } answers { firstArg() }
        every { voteRepositoryPort.save(any()) } returns vote

        drawService.draw(vote)

        verify {
            drawHistoryRepositoryPort.save(
                match { it.audit?.candidates == options.drop(1).map { o -> o.id.toString() }.sorted() }
            )
        }
    }
}
