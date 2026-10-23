package com.juncevich.fate.vote

import com.juncevich.fate.account.UserProfile
import com.juncevich.fate.account.UserQueryService
import com.juncevich.fate.notification.EmailBudget
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.TooManyRequestsException
import com.juncevich.fate.vote.domain.DrawHistory
import com.juncevich.fate.vote.domain.DrawHistoryRepository
import com.juncevich.fate.vote.domain.Participant
import com.juncevich.fate.vote.domain.ParticipantInvited
import com.juncevich.fate.vote.domain.Vote
import com.juncevich.fate.vote.domain.VoteDrawn
import com.juncevich.fate.vote.domain.VoteOption
import com.juncevich.fate.vote.domain.VoteRepository
import com.juncevich.fate.vote.domain.VoteSummary
import com.juncevich.fate.vote.domain.WinnerPicker
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

class VoteServiceTest {
    private val votes = mockk<VoteRepository>(relaxed = true)
    private val history = mockk<DrawHistoryRepository>(relaxed = true)
    private val userQueryService = mockk<UserQueryService>()
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private val emailBudget = mockk<EmailBudget>(relaxed = true)

    private val service =
        VoteService(votes, history, userQueryService, WinnerPicker(), events, meterRegistry, emailBudget)

    private val creator = UserProfile(UUID.randomUUID(), "creator@test.com", "Creator")

    @BeforeEach
    fun setUp() {
        every { userQueryService.findProfileById(creator.id) } returns creator
        every { userQueryService.findProfilesByEmailIn(any()) } returns listOf(creator)
        every { userQueryService.findProfileByEmail(any()) } returns null
        every { emailBudget.tryReserve(any(), any()) } returns true
    }

    @AfterEach
    fun tearDown() = meterRegistry.close()

    private fun stored(
        drawTarget: DrawTarget = DrawTarget.PARTICIPANTS,
        mode: VoteMode = VoteMode.SIMPLE,
        status: VoteStatus = VoteStatus.PENDING,
        participants: List<String> = listOf(creator.email),
        options: List<String> = emptyList(),
    ): Vote {
        val vote =
            Vote(
                id = UUID.randomUUID(),
                title = "Vote",
                description = null,
                creatorId = creator.id,
                mode = mode,
                drawTarget = drawTarget,
                status = status,
                currentRound = 1,
                createdAt = Instant.now(),
                version = 0,
                participants = participants.map { Participant(it) },
                options = options.mapIndexed { position, title -> VoteOption(title, position) }
            )
        every { votes.find(vote.id) } returns vote
        every { votes.findForUpdate(vote.id) } returns vote
        return vote
    }

    // ── createVote ───────────────────────────────────────────────────────────────

    @Test
    fun `createVote stores the vote, reserves and sends one invitation per invitee`() {
        val saved = slot<Vote>()
        every { votes.save(capture(saved)) } returns Unit

        val view =
            service.createVote(
                creator.id,
                CreateVoteCommand(title = "Vote", participantEmails = listOf("p@test.com", creator.email), options = listOf("A", "B"))
            )

        assertEquals(listOf(creator.email, "p@test.com"), saved.captured.participants.map { it.email })
        assertEquals(
            "Creator",
            saved.captured.participants
                .first()
                .displayName
        )
        assertEquals(listOf("A", "B"), view.options.map { it.title })
        assertEquals(DrawTarget.OPTIONS, view.drawTarget)
        assertTrue(view.isCreator)
        assertNull(view.lastResult)
        verify(exactly = 1) { emailBudget.reserveOrThrow(creator.id, 1) }
        verify(exactly = 1) { events.publishEvent(ParticipantInvited(view.id, "Vote", "Creator", "p@test.com")) }
        verify(exactly = 1) { events.publishEvent(any<Any>()) }
        assertEquals(
            1.0,
            meterRegistry
                .get("vote.created")
                .tag("mode", "SIMPLE")
                .counter()
                .count()
        )
    }

    @Test
    fun `createVote over the email budget stores nothing and sends no invitations`() {
        every { emailBudget.reserveOrThrow(creator.id, 2) } throws TooManyRequestsException("Email limit reached", ErrorCode.EMAIL_LIMIT_REACHED)

        assertThrows<TooManyRequestsException> {
            service.createVote(creator.id, CreateVoteCommand(title = "Vote", participantEmails = listOf("a@test.com", "b@test.com", creator.email)))
        }

        verify(exactly = 0) { votes.save(any()) }
        verify(exactly = 0) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `createVote rejected by the vote's rules stores nothing`() {
        assertThrows<BadRequestException> {
            service.createVote(creator.id, CreateVoteCommand(title = "Vote", participantEmails = listOf("not-an-email")))
        }
        assertThrows<BadRequestException> {
            service.createVote(creator.id, CreateVoteCommand(title = "Vote", options = listOf("A"), drawTarget = DrawTarget.PARTICIPANTS))
        }
        verify(exactly = 0) { votes.save(any()) }
        verify(exactly = 0) { emailBudget.reserveOrThrow(any(), any()) }
    }

    @Test
    fun `createVote for an unknown user fails`() {
        val unknown = UUID.randomUUID()
        every { userQueryService.findProfileById(unknown) } returns null

        assertEquals(
            ErrorCode.USER_NOT_FOUND,
            assertThrows<NotFoundException> { service.createVote(unknown, CreateVoteCommand(title = "Vote")) }.code
        )
    }

    // ── reads ────────────────────────────────────────────────────────────────────

    @Test
    fun `listVotes maps summaries and marks the requester's own votes`() {
        val pageable = PageRequest.of(0, 20)
        val own = summary(creator.id)
        val foreign = summary(UUID.randomUUID())
        every { votes.findSummaries(creator.id, creator.email, pageable) } returns PageImpl(listOf(own, foreign), pageable, 2)

        val page = service.listVotes(creator.id, creator.email, pageable)

        assertEquals(listOf(true, false), page.content.map { it.isCreator })
        assertEquals(3L, page.content.first().participantCount)
        assertEquals(2, page.totalElements)
    }

    @Test
    fun `getVote shows a participant the vote with its last result`() {
        val vote = stored(participants = listOf(creator.email, "p@test.com"))
        every { history.findLatest(vote.id) } returns DrawHistory.ParticipantWinner(voteId = vote.id, email = "p@test.com", displayName = "Pat", round = 1)

        val view = service.getVote(vote.id, UUID.randomUUID(), "p@test.com")

        assertFalse(view.isCreator)
        assertEquals(listOf(creator.email, "p@test.com"), view.participants.map { it.email })
        assertEquals(Winner.Participant("p@test.com", "Pat"), view.lastResult?.winner)
    }

    @Test
    fun `reads refuse a requester who is neither creator nor participant`() {
        val vote = stored()
        val stranger = UUID.randomUUID()

        assertThrows<ForbiddenException> { service.getVote(vote.id, stranger, "other@test.com") }
        assertThrows<ForbiddenException> { service.getHistory(vote.id, stranger, "other@test.com") }
        assertThrows<ForbiddenException> { service.getLastResult(vote.id, stranger, "other@test.com") }
    }

    @Test
    fun `reads of an unknown vote fail with VOTE_NOT_FOUND`() {
        val id = UUID.randomUUID()
        every { votes.find(id) } returns null

        listOf<() -> Unit>(
            { service.getVote(id, creator.id, creator.email) },
            { service.getHistory(id, creator.id, creator.email) },
            { service.getLastResult(id, creator.id, creator.email) }
        ).forEach { assertEquals(ErrorCode.VOTE_NOT_FOUND, assertThrows<NotFoundException>(it).code) }
    }

    @Test
    fun `getLastResult returns the latest option winner, or null before any draw`() {
        val vote = stored(DrawTarget.OPTIONS, participants = listOf(creator.email, "p@test.com"))
        every { history.findLatest(vote.id) } returnsMany
            listOf(DrawHistory.OptionWinner(voteId = vote.id, optionId = UUID.randomUUID(), optionTitle = "Pizza", round = 2), null)

        val result = service.getLastResult(vote.id, UUID.randomUUID(), "p@test.com")
        assertEquals(Winner.Option("Pizza"), result?.winner)
        assertEquals(2, result?.round)

        assertNull(service.getLastResult(vote.id, creator.id, creator.email))
    }

    @Test
    fun `history pages are bounded to 100 entries`() {
        val vote = stored()
        every { history.findPage(vote.id, PageRequest.of(1, 100)) } returns PageImpl(emptyList(), PageRequest.of(1, 100), 100)

        assertEquals(100, service.getHistory(vote.id, creator.id, creator.email, PageRequest.of(1, 100)).totalElements)
        listOf(PageRequest.of(0, 101), Pageable.unpaged()).forEach { pageable ->
            assertEquals(
                ErrorCode.PAGE_INVALID,
                assertThrows<BadRequestException> { service.getHistory(vote.id, creator.id, creator.email, pageable) }.code
            )
        }
    }

    @Test
    fun `the legacy history returns the first page of at most 100 entries`() {
        val vote = stored()
        every { history.findPage(vote.id, PageRequest.of(0, 100)) } returns
            PageImpl(listOf(DrawHistory.OptionWinner(voteId = vote.id, optionId = null, optionTitle = "Gone", round = 1)))

        assertEquals(listOf(Winner.Option("Gone")), service.getHistory(vote.id, creator.id, creator.email).map { it.winner })
    }

    // ── changes ──────────────────────────────────────────────────────────────────

    @Test
    fun `addParticipant stores the participant and sends the invitation`() {
        val vote = stored()
        every { userQueryService.findProfileByEmail("new@test.com") } returns UserProfile(UUID.randomUUID(), "new@test.com", "Newton")

        service.addParticipant(vote.id, creator.id, "New@test.com")

        verify { votes.save(match { v -> v.participants.any { it.email == "new@test.com" && it.displayName == "Newton" } }) }
        verify(exactly = 1) { emailBudget.reserveOrThrow(creator.id, 1) }
        verify(exactly = 1) { events.publishEvent(ParticipantInvited(vote.id, vote.title, "Creator", "new@test.com")) }
    }

    @Test
    fun `addParticipant over the email budget adds nobody`() {
        val vote = stored()
        every { emailBudget.reserveOrThrow(creator.id, 1) } throws TooManyRequestsException("Email limit reached", ErrorCode.EMAIL_LIMIT_REACHED)

        assertThrows<TooManyRequestsException> { service.addParticipant(vote.id, creator.id, "new@test.com") }

        verify(exactly = 0) { votes.save(any()) }
        verify(exactly = 0) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `a change the vote refuses is neither stored nor announced`() {
        val vote = stored(status = VoteStatus.DRAWN)

        assertThrows<ForbiddenException> { service.addParticipant(vote.id, UUID.randomUUID(), "new@test.com") }
        assertThrows<ConflictException> { service.addParticipant(vote.id, creator.id, "new@test.com") }
        assertThrows<ConflictException> { service.removeParticipant(vote.id, creator.id, creator.email) }

        verify(exactly = 0) { votes.save(any()) }
        verify(exactly = 0) { emailBudget.reserveOrThrow(any(), any()) }
        verify(exactly = 0) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `composition changes are applied to the locked vote and stored`() {
        val vote = stored(DrawTarget.OPTIONS, participants = listOf(creator.email, "p@test.com"), options = listOf("A", "B"))
        val (a, b) = vote.options

        service.removeParticipant(vote.id, creator.id, "p@test.com")
        service.addOption(vote.id, creator.id, " C ")
        service.reorderOptions(vote.id, creator.id, listOf(b.id, a.id, vote.options.last().id))
        service.removeOption(vote.id, creator.id, a.id)

        assertEquals(listOf(creator.email), vote.participants.map { it.email })
        assertEquals(listOf("B", "C"), vote.options.map { it.title })
        verify(exactly = 4) { votes.findForUpdate(vote.id) }
        verify(exactly = 4) { votes.save(vote) }
        verify(exactly = 0) { votes.find(any()) }
    }

    @Test
    fun `changes to an unknown vote fail with VOTE_NOT_FOUND`() {
        val id = UUID.randomUUID()
        every { votes.findForUpdate(id) } returns null

        listOf<() -> Unit>(
            { service.addParticipant(id, creator.id, "new@test.com") },
            { service.addOption(id, creator.id, "A") },
            { service.draw(id, creator.id) },
            { service.reopen(id, creator.id) },
            { service.closeVote(id, creator.id) },
            { service.deleteVote(id, creator.id) }
        ).forEach { assertEquals(ErrorCode.VOTE_NOT_FOUND, assertThrows<NotFoundException>(it).code) }
    }

    @Test
    fun `reopen and close change the status and store it`() {
        val vote = stored(status = VoteStatus.DRAWN)

        service.reopen(vote.id, creator.id)
        assertEquals(VoteStatus.PENDING, vote.status)
        service.closeVote(vote.id, creator.id)
        assertEquals(VoteStatus.CLOSED, vote.status)

        verify(exactly = 2) { votes.save(vote) }
    }

    @Test
    fun `only the creator deletes a vote`() {
        val vote = stored()

        assertThrows<ForbiddenException> { service.deleteVote(vote.id, UUID.randomUUID()) }
        verify(exactly = 0) { votes.delete(any()) }

        service.deleteVote(vote.id, creator.id)
        verify(exactly = 1) { votes.delete(vote.id) }
    }

    // ── draw ─────────────────────────────────────────────────────────────────────

    @Test
    fun `draw records the entry, stores the vote, counts it and notifies every participant`() {
        val vote = stored(participants = listOf("winner@test.com"))
        val recorded = slot<DrawHistory>()
        every { history.save(capture(recorded)) } returns Unit

        val result = service.draw(vote.id, creator.id)

        assertEquals(Winner.Participant("winner@test.com", null), result.winner)
        assertEquals("winner@test.com", (recorded.captured as DrawHistory.ParticipantWinner).email)
        assertFalse(result.newRoundStarted)
        verify { votes.save(match { it.status == VoteStatus.DRAWN }) }
        verify(exactly = 1) { emailBudget.tryReserve(creator.id, 1) }
        verify(exactly = 1) { events.publishEvent(VoteDrawn(vote.id, vote.title, result, listOf("winner@test.com"))) }
        assertEquals(
            1.0,
            meterRegistry
                .get("vote.draw.performed")
                .tag("mode", "SIMPLE")
                .counter()
                .count()
        )
    }

    @Test
    fun `draw of an option reports the option and reads the round's history for fair rotation`() {
        val vote = stored(DrawTarget.OPTIONS, VoteMode.FAIR_ROTATION, options = listOf("Pizza"))
        every { history.findByRound(vote.id, 1) } returns emptyList()

        val result = service.draw(vote.id, creator.id)

        assertEquals(Winner.Option("Pizza"), result.winner)
        assertEquals(1, result.round)
        verify { history.findByRound(vote.id, 1) }
        verify { history.save(match { it is DrawHistory.OptionWinner && it.optionTitle == "Pizza" }) }
    }

    @Test
    fun `draw over the email budget still draws but sends no result emails`() {
        val vote = stored(participants = listOf("winner@test.com", creator.email))
        every { emailBudget.tryReserve(creator.id, 2) } returns false

        service.draw(vote.id, creator.id)

        verify { history.save(any()) }
        verify(exactly = 0) { events.publishEvent(any<VoteDrawn>()) }
    }

    @Test
    fun `a refused draw records nothing`() {
        val vote = stored(status = VoteStatus.DRAWN)

        assertThrows<ConflictException> { service.draw(vote.id, creator.id) }

        verify(exactly = 0) { history.save(any()) }
        verify(exactly = 0) { votes.save(any()) }
        verify(exactly = 0) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `the draw metric does not create a time series per round`() {
        val vote = stored(DrawTarget.PARTICIPANTS, VoteMode.FAIR_ROTATION, participants = listOf("a@test.com"))
        every { history.findByRound(vote.id, any()) } answers {
            listOf(DrawHistory.ParticipantWinner(voteId = vote.id, email = "a@test.com", round = secondArg()))
        }

        repeat(3) {
            service.draw(vote.id, creator.id)
            vote.reopen(creator.id)
        }

        assertEquals(4, vote.currentRound)
        val counter = meterRegistry.get("vote.draw.performed").counter()
        assertEquals(3.0, counter.count())
        assertNull(counter.id.getTag("round"))
    }

    private fun summary(creatorId: UUID) = VoteSummary(UUID.randomUUID(), "Vote", creatorId, VoteMode.SIMPLE, VoteStatus.PENDING, 1, 3, Instant.now())
}
