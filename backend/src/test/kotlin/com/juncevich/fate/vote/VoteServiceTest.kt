package com.juncevich.fate.vote

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.UserQueryService
import com.juncevich.fate.auth.toProfile
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.TooManyRequestsException
import com.juncevich.fate.vote.internal.DrawService
import com.juncevich.fate.vote.internal.ParticipantInvited
import com.juncevich.fate.vote.internal.VoteDrawn
import com.juncevich.fate.vote.internal.domain.DrawHistory
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.domain.VoteOption
import com.juncevich.fate.vote.internal.domain.VoteParticipant
import com.juncevich.fate.vote.internal.notification.EmailBudget
import com.juncevich.fate.vote.internal.port.DrawHistoryRepositoryPort
import com.juncevich.fate.vote.internal.port.ParticipantCount
import com.juncevich.fate.vote.internal.port.ParticipantRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteOptionRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteRepositoryPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class VoteServiceTest {
    private val voteRepositoryPort = mockk<VoteRepositoryPort>()
    private val participantRepositoryPort = mockk<ParticipantRepositoryPort>()
    private val voteOptionRepositoryPort = mockk<VoteOptionRepositoryPort>()
    private val drawHistoryRepositoryPort = mockk<DrawHistoryRepositoryPort>()
    private val userQueryService = mockk<UserQueryService>()
    private val drawService = mockk<DrawService>()
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)
    private val meterRegistry = mockk<MeterRegistry>()
    private val counter = mockk<Counter>(relaxed = true)
    private val emailBudget = mockk<EmailBudget>(relaxed = true)

    private val voteService =
        VoteService(
            voteRepositoryPort,
            participantRepositoryPort,
            voteOptionRepositoryPort,
            drawHistoryRepositoryPort,
            userQueryService,
            drawService,
            events,
            meterRegistry,
            emailBudget
        )

    @BeforeEach
    fun setUp() {
        every { meterRegistry.counter(any<String>(), *anyVararg<String>()) } returns counter
        every { emailBudget.tryReserve(any(), any()) } returns true
    }

    private fun makeUser(
        id: UUID = UUID.randomUUID(),
        email: String = "user@test.com",
    ) = User(id = id, email = email, passwordHash = "hash", displayName = "Test User")

    private fun makeVote(
        id: UUID = UUID.randomUUID(),
        creator: User,
        mode: VoteMode = VoteMode.SIMPLE,
        status: VoteStatus = VoteStatus.PENDING,
        drawTarget: DrawTarget = DrawTarget.PARTICIPANTS,
    ) = Vote(id = id, title = "Test Vote", creator = creator.toProfile(), mode = mode, drawTarget = drawTarget)
        .also { it.status = status }

    @Test
    fun `createVote - creates vote, participants, options and sends invitations`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        val participant = VoteParticipant(voteId = vote.id, email = creator.email)

        every { userQueryService.findProfileById(creator.id) } returns creator.toProfile()
        every { voteRepositoryPort.save(any()) } returns vote
        every { userQueryService.findProfilesByEmailIn(any()) } returns listOf(creator.toProfile())
        every { participantRepositoryPort.saveAll(any<List<VoteParticipant>>()) } returns listOf(participant)
        every { voteOptionRepositoryPort.saveAll(any<List<VoteOption>>()) } answers { firstArg() }

        val request =
            CreateVoteCommand(
                title = "Vote",
                description = null,
                mode = VoteMode.SIMPLE,
                participantEmails = listOf("p@test.com"),
                options = listOf("Option A", "Option B")
            )

        voteService.createVote(creator.id, request)

        verify { participantRepositoryPort.saveAll(any<List<VoteParticipant>>()) }
        verify { voteOptionRepositoryPort.saveAll(any<List<VoteOption>>()) }
        verify(exactly = 1) {
            events.publishEvent(ParticipantInvited(vote.id, vote.title, creator.displayName, "p@test.com"))
        }
        verify(exactly = 0) { events.publishEvent(match<ParticipantInvited> { it.recipientEmail == creator.email }) }
        verify(exactly = 1) { emailBudget.reserveOrThrow(creator.id, 1) }
    }

    @Test
    fun `createVote - over the email budget - saves nothing and sends no invitations`() {
        val creator = makeUser()
        every { userQueryService.findProfileById(creator.id) } returns creator.toProfile()
        every { emailBudget.reserveOrThrow(creator.id, 2) } throws TooManyRequestsException("Email limit reached")

        assertThrows<TooManyRequestsException> {
            voteService.createVote(
                creator.id,
                CreateVoteCommand(
                    title = "Vote",
                    description = null,
                    mode = VoteMode.SIMPLE,
                    participantEmails = listOf("a@test.com", "b@test.com", creator.email),
                    options = null
                )
            )
        }

        verify(exactly = 0) { voteRepositoryPort.save(any()) }
        verify(exactly = 0) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `createVote - rejects malformed participant email`() {
        val creator = makeUser()
        every { userQueryService.findProfileById(creator.id) } returns creator.toProfile()

        val request =
            CreateVoteCommand(
                title = "Vote",
                description = null,
                mode = VoteMode.SIMPLE,
                participantEmails = listOf("not-an-email"),
                options = null
            )

        assertThrows<BadRequestException> { voteService.createVote(creator.id, request) }
        verify(exactly = 0) { voteRepositoryPort.save(any()) }
    }

    @Test
    fun `getVote - throws when requester has no access`() {
        val creator = makeUser()
        val requester = makeUser(email = "other@test.com")
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, requester.email) } returns false

        assertThrows<ForbiddenException> {
            voteService.getVote(vote.id, requester.id, requester.email)
        }
    }

    @Test
    fun `getHistory - throws when requester has no access`() {
        val creator = makeUser()
        val requester = makeUser(email = "other@test.com")
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, requester.email) } returns false

        assertThrows<ForbiddenException> {
            voteService.getHistory(vote.id, requester.id, requester.email)
        }
    }

    @Test
    fun `getLastResult - returns latest option winner for a participant`() {
        val creator = makeUser()
        val participant = makeUser(email = "p@test.com")
        val vote = makeVote(creator = creator)
        val last = DrawHistory.OptionWinner(voteId = vote.id, optionId = UUID.randomUUID(), optionTitle = "Pizza", round = 2)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, participant.email) } returns true
        every { drawHistoryRepositoryPort.findTopByVoteIdOrderByDrawnAtDescIdDesc(vote.id) } returns last

        val result = voteService.getLastResult(vote.id, participant.id, participant.email)

        assertEquals("Pizza", result?.winnerOptionTitle)
        assertNull(result?.winnerEmail)
        assertNull(result?.winnerDisplayName)
        assertEquals(2, result?.round)
    }

    @Test
    fun `getLastResult - returns null when vote has not been drawn`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { drawHistoryRepositoryPort.findTopByVoteIdOrderByDrawnAtDescIdDesc(vote.id) } returns null

        assertNull(voteService.getLastResult(vote.id, creator.id, creator.email))
    }

    @Test
    fun `getLastResult - throws when requester has no access`() {
        val creator = makeUser()
        val requester = makeUser(email = "other@test.com")
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, requester.email) } returns false

        assertThrows<ForbiddenException> { voteService.getLastResult(vote.id, requester.id, requester.email) }
    }

    @Test
    fun `getLastResult - throws when vote does not exist`() {
        val id = UUID.randomUUID()
        every { voteRepositoryPort.findById(id) } returns null
        every { voteRepositoryPort.findByIdForUpdate(id) } returns null

        assertThrows<NotFoundException> { voteService.getLastResult(id, UUID.randomUUID(), "x@test.com") }
    }

    @Test
    fun `draw - throws when vote does not exist`() {
        val id = UUID.randomUUID()
        every { voteRepositoryPort.findByIdForUpdate(id) } returns null

        assertThrows<NotFoundException> { voteService.draw(id, UUID.randomUUID()) }
    }

    @Test
    fun `addParticipant - throws when requester is not the creator`() {
        val creator = makeUser()
        val otherUser = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote

        assertThrows<ForbiddenException> {
            voteService.addParticipant(vote.id, otherUser.id, "new@test.com")
        }
    }

    @Test
    fun `addParticipant - throws when vote is not PENDING`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.DRAWN)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote

        assertThrows<ConflictException> {
            voteService.addParticipant(vote.id, creator.id, "new@test.com")
        }
    }

    @Test
    fun `addParticipant - throws when participant already exists`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, "dup@test.com") } returns true

        assertThrows<ConflictException> {
            voteService.addParticipant(vote.id, creator.id, "dup@test.com")
        }
    }

    @Test
    fun `addParticipant - saves participant and sends invitation`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        val participant = VoteParticipant(voteId = vote.id, email = "new@test.com")

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, "new@test.com") } returns false
        every { participantRepositoryPort.countByVoteIds(listOf(vote.id)) } returns
            listOf(ParticipantCount(vote.id, 1))
        every { userQueryService.findProfileByEmail("new@test.com") } returns null
        every { participantRepositoryPort.save(any()) } returns participant

        voteService.addParticipant(vote.id, creator.id, "new@test.com")

        verify { participantRepositoryPort.save(any()) }
        verify(exactly = 1) {
            events.publishEvent(ParticipantInvited(vote.id, vote.title, creator.displayName, "new@test.com"))
        }
        verify(exactly = 1) { emailBudget.reserveOrThrow(creator.id, 1) }
    }

    @Test
    fun `addParticipant - over the email budget - adds nobody`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, "new@test.com") } returns false
        every { participantRepositoryPort.countByVoteIds(listOf(vote.id)) } returns
            listOf(ParticipantCount(vote.id, 1))
        every { emailBudget.reserveOrThrow(creator.id, 1) } throws TooManyRequestsException("Email limit reached")

        assertThrows<TooManyRequestsException> { voteService.addParticipant(vote.id, creator.id, "new@test.com") }

        verify(exactly = 0) { participantRepositoryPort.save(any()) }
        verify(exactly = 0) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `removeParticipant - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote

        assertThrows<ForbiddenException> {
            voteService.removeParticipant(vote.id, other.id, "p@test.com")
        }
    }

    @Test
    fun `removeParticipant - throws when vote is not PENDING`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.CLOSED)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote

        assertThrows<ConflictException> {
            voteService.removeParticipant(vote.id, creator.id, "p@test.com")
        }
    }

    @Test
    fun `removeParticipant - deletes participant`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { participantRepositoryPort.deleteByVoteIdAndEmail(vote.id, "p@test.com") } just Runs

        voteService.removeParticipant(vote.id, creator.id, "p@test.com")

        verify { participantRepositoryPort.deleteByVoteIdAndEmail(vote.id, "p@test.com") }
    }

    @Test
    fun `draw - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote

        assertThrows<ForbiddenException> {
            voteService.draw(vote.id, other.id)
        }
    }

    @Test
    fun `draw - delegates to DrawService and notifies participants`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        val drawResult = DrawResult("winner@test.com", "Winner", null, 1, false)
        val participant = VoteParticipant(voteId = vote.id, email = "winner@test.com")

        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { drawService.draw(vote) } returns drawResult
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns listOf(participant)

        val result = voteService.draw(vote.id, creator.id)

        assertEquals(drawResult.winnerEmail, result.winnerEmail)
        verify { drawService.draw(vote) }
        verify(exactly = 1) {
            events.publishEvent(VoteDrawn(vote.id, vote.title, drawResult, listOf("winner@test.com")))
        }
        verify(exactly = 1) { emailBudget.tryReserve(creator.id, 1) }
    }

    @Test
    fun `draw - over the email budget - still draws but sends no result emails`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        val drawResult = DrawResult("winner@test.com", "Winner", null, 1, false)
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { drawService.draw(vote) } returns drawResult
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns
            listOf(
                VoteParticipant(voteId = vote.id, email = "winner@test.com"),
                VoteParticipant(voteId = vote.id, email = creator.email)
            )
        every { emailBudget.tryReserve(creator.id, 2) } returns false

        assertEquals(drawResult, voteService.draw(vote.id, creator.id))

        verify(exactly = 0) { events.publishEvent(any<VoteDrawn>()) }
    }

    @Test
    fun `closeVote - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote

        assertThrows<ForbiddenException> { voteService.closeVote(vote.id, other.id) }
    }

    @Test
    fun `closeVote - sets status to CLOSED`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { voteRepositoryPort.save(any()) } returns vote

        voteService.closeVote(vote.id, creator.id)

        assertEquals(VoteStatus.CLOSED, vote.status)
        verify { voteRepositoryPort.save(vote) }
    }

    @Test
    fun `closeVote - throws when vote is already closed`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.CLOSED)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote

        assertThrows<ConflictException> { voteService.closeVote(vote.id, creator.id) }
    }

    @Test
    fun `deleteVote - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote

        assertThrows<ForbiddenException> { voteService.deleteVote(vote.id, other.id) }
    }

    @Test
    fun `deleteVote - deletes the vote`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { voteRepositoryPort.delete(vote) } just Runs

        voteService.deleteVote(vote.id, creator.id)

        verify { voteRepositoryPort.delete(vote) }
    }

    @Test
    fun `reopen - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.DRAWN)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote

        assertThrows<ForbiddenException> { voteService.reopen(vote.id, other.id) }
    }

    @Test
    fun `reopen - delegates to DrawService`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.DRAWN)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { drawService.reopen(vote) } just Runs

        voteService.reopen(vote.id, creator.id)

        verify { drawService.reopen(vote) }
    }

    @Test
    fun `removeOption - deletes only option belonging to vote`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        val optionId = UUID.randomUUID()

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { voteOptionRepositoryPort.deleteByVoteIdAndId(vote.id, optionId) } just Runs

        voteService.removeOption(vote.id, creator.id, optionId)

        verify { voteOptionRepositoryPort.deleteByVoteIdAndId(vote.id, optionId) }
    }

    @Test
    fun `createVote - rejects a description over the limit`() {
        val creator = makeUser()
        every { userQueryService.findProfileById(creator.id) } returns creator.toProfile()
        val request =
            CreateVoteCommand(title = "Vote", description = "x".repeat(VoteLimits.MAX_DESCRIPTION_LENGTH + 1))

        assertThrows<BadRequestException> { voteService.createVote(creator.id, request) }
        verify(exactly = 0) { voteRepositoryPort.save(any()) }
    }

    @Test
    fun `createVote - rejects more participants than the limit, the creator included`() {
        val creator = makeUser()
        every { userQueryService.findProfileById(creator.id) } returns creator.toProfile()
        // Exactly the limit of invitees, plus the creator, exceeds the limit.
        val request =
            CreateVoteCommand(
                title = "Vote",
                participantEmails = List(VoteLimits.MAX_PARTICIPANTS) { "p$it@test.com" }
            )

        assertThrows<BadRequestException> { voteService.createVote(creator.id, request) }
        verify(exactly = 0) { voteRepositoryPort.save(any()) }
    }

    @Test
    fun `createVote - rejects an oversized participant list before normalizing it`() {
        val creator = makeUser()
        every { userQueryService.findProfileById(creator.id) } returns creator.toProfile()
        val request =
            CreateVoteCommand(title = "Vote", participantEmails = List(VoteLimits.MAX_PARTICIPANTS + 1) { "not-an-email" })

        val ex = assertThrows<BadRequestException> { voteService.createVote(creator.id, request) }
        assertEquals("A vote can have at most ${VoteLimits.MAX_PARTICIPANTS} participants", ex.message)
    }

    @Test
    fun `createVote - rejects more options than the limit`() {
        val creator = makeUser()
        every { userQueryService.findProfileById(creator.id) } returns creator.toProfile()
        val request = CreateVoteCommand(title = "Vote", options = List(VoteLimits.MAX_OPTIONS + 1) { "Option $it" })

        assertThrows<BadRequestException> { voteService.createVote(creator.id, request) }
        verify(exactly = 0) { voteRepositoryPort.save(any()) }
    }

    @Test
    fun `addParticipant - rejects when the vote is full`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, "new@test.com") } returns false
        every { participantRepositoryPort.countByVoteIds(listOf(vote.id)) } returns
            listOf(ParticipantCount(vote.id, VoteLimits.MAX_PARTICIPANTS.toLong()))

        assertThrows<BadRequestException> { voteService.addParticipant(vote.id, creator.id, "new@test.com") }
        verify(exactly = 0) { participantRepositoryPort.save(any()) }
        verify(exactly = 0) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `addOption - saves a trimmed option`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, drawTarget = DrawTarget.OPTIONS)
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns emptyList()
        every { voteOptionRepositoryPort.save(any()) } answers { firstArg() }

        voteService.addOption(vote.id, creator.id, "  Option  ")

        verify { voteOptionRepositoryPort.save(match { it.title == "Option" && it.voteId == vote.id }) }
    }

    @Test
    fun `addOption - appends after the highest position, even with gaps left by removals`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, drawTarget = DrawTarget.OPTIONS)
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns
            listOf(VoteOption(voteId = vote.id, title = "A", position = 0), VoteOption(voteId = vote.id, title = "C", position = 2))
        every { voteOptionRepositoryPort.save(any()) } answers { firstArg() }

        voteService.addOption(vote.id, creator.id, "D")

        verify { voteOptionRepositoryPort.save(match { it.position == 3 }) }
    }

    private fun optionsVoteWith(
        creator: User,
        status: VoteStatus = VoteStatus.PENDING,
    ): Pair<Vote, List<VoteOption>> {
        val vote = makeVote(creator = creator, status = status, drawTarget = DrawTarget.OPTIONS)
        val options = List(3) { VoteOption(voteId = vote.id, title = "Option $it", position = it) }
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns options
        every { voteOptionRepositoryPort.updatePositions(vote.id, any()) } returns Unit
        return vote to options
    }

    @Test
    fun `reorderOptions - stores the new order`() {
        val creator = makeUser()
        val (vote, options) = optionsVoteWith(creator)
        val newOrder = options.map { it.id }.reversed()

        voteService.reorderOptions(vote.id, creator.id, newOrder)

        verify { voteOptionRepositoryPort.updatePositions(vote.id, newOrder) }
    }

    @Test
    fun `reorderOptions - rejects an order that is not a permutation of the options`() {
        val creator = makeUser()
        val (vote, options) = optionsVoteWith(creator)
        val ids = options.map { it.id }

        listOf(ids.drop(1), ids + ids[0], ids.drop(1) + UUID.randomUUID()).forEach { order ->
            assertThrows<BadRequestException> { voteService.reorderOptions(vote.id, creator.id, order) }
        }
        verify(exactly = 0) { voteOptionRepositoryPort.updatePositions(any(), any()) }
    }

    @Test
    fun `reorderOptions - only the creator may reorder, and only while pending`() {
        val creator = makeUser()
        val (vote, options) = optionsVoteWith(creator)
        assertThrows<ForbiddenException> { voteService.reorderOptions(vote.id, UUID.randomUUID(), options.map { it.id }) }

        val (drawn, drawnOptions) = optionsVoteWith(creator, VoteStatus.DRAWN)
        assertThrows<ConflictException> { voteService.reorderOptions(drawn.id, creator.id, drawnOptions.map { it.id }) }
    }

    @Test
    fun `addOption - rejects when the vote already has the maximum number of options`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, drawTarget = DrawTarget.OPTIONS)
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote
        every { voteOptionRepositoryPort.findAllByVoteIdOrderedByPosition(vote.id) } returns
            List(VoteLimits.MAX_OPTIONS) { VoteOption(voteId = vote.id, title = "Option $it", position = it) }

        assertThrows<BadRequestException> { voteService.addOption(vote.id, creator.id, "One more") }
        verify(exactly = 0) { voteOptionRepositoryPort.save(any()) }
    }

    @Test
    fun `addOption - rejects options on a vote that draws from participants`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        every { voteRepositoryPort.findByIdForUpdate(vote.id) } returns vote

        assertThrows<ConflictException> { voteService.addOption(vote.id, creator.id, "Pizza") }
        verify(exactly = 0) { voteOptionRepositoryPort.save(any()) }
    }

    private fun stubCreate(creator: User) {
        every { userQueryService.findProfileById(creator.id) } returns creator.toProfile()
        every { voteRepositoryPort.save(any()) } answers { firstArg() }
        every { userQueryService.findProfilesByEmailIn(any()) } returns listOf(creator.toProfile())
        every { participantRepositoryPort.saveAll(any<List<VoteParticipant>>()) } answers { firstArg() }
        every { voteOptionRepositoryPort.saveAll(any<List<VoteOption>>()) } answers { firstArg() }
    }

    private fun command(
        options: List<String>?,
        drawTarget: DrawTarget?,
    ) = CreateVoteCommand(title = "Vote", options = options, drawTarget = drawTarget)

    @Test
    fun `createVote - infers the draw target from the options when none is given`() {
        val creator = makeUser()
        stubCreate(creator)

        assertEquals(DrawTarget.OPTIONS, voteService.createVote(creator.id, command(listOf("A"), null)).drawTarget)
        assertEquals(DrawTarget.PARTICIPANTS, voteService.createVote(creator.id, command(null, null)).drawTarget)
    }

    @Test
    fun `createVote - an options vote may start without options`() {
        val creator = makeUser()
        stubCreate(creator)

        val vote = voteService.createVote(creator.id, command(emptyList(), DrawTarget.OPTIONS))

        assertEquals(DrawTarget.OPTIONS, vote.drawTarget)
        verify { voteRepositoryPort.save(match { it.drawTarget == DrawTarget.OPTIONS }) }
    }

    @Test
    fun `createVote - rejects options on a vote that draws from participants`() {
        val creator = makeUser()
        stubCreate(creator)

        assertThrows<BadRequestException> {
            voteService.createVote(creator.id, command(listOf("A"), DrawTarget.PARTICIPANTS))
        }
        verify(exactly = 0) { voteRepositoryPort.save(any()) }
    }
}
