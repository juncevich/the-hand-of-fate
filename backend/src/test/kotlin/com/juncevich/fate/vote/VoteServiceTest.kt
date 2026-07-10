package com.juncevich.fate.vote

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.UserQueryService
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.vote.internal.DrawService
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.domain.VoteOption
import com.juncevich.fate.vote.internal.domain.VoteParticipant
import com.juncevich.fate.vote.internal.port.DrawHistoryRepositoryPort
import com.juncevich.fate.vote.internal.port.NotificationPort
import com.juncevich.fate.vote.internal.port.ParticipantRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteOptionRepositoryPort
import com.juncevich.fate.vote.internal.port.VoteRepositoryPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class VoteServiceTest {
    private val voteRepositoryPort = mockk<VoteRepositoryPort>()
    private val participantRepositoryPort = mockk<ParticipantRepositoryPort>()
    private val voteOptionRepositoryPort = mockk<VoteOptionRepositoryPort>()
    private val drawHistoryRepositoryPort = mockk<DrawHistoryRepositoryPort>()
    private val userQueryService = mockk<UserQueryService>()
    private val drawService = mockk<DrawService>()
    private val notificationPort = mockk<NotificationPort>(relaxed = true)
    private val meterRegistry = mockk<MeterRegistry>()
    private val counter = mockk<Counter>(relaxed = true)

    private val voteService =
        VoteService(
            voteRepositoryPort,
            participantRepositoryPort,
            voteOptionRepositoryPort,
            drawHistoryRepositoryPort,
            userQueryService,
            drawService,
            notificationPort,
            meterRegistry
        )

    @BeforeEach
    fun setUp() {
        every { meterRegistry.counter(any<String>(), *anyVararg<String>()) } returns counter
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
    ) = Vote(id = id, title = "Test Vote", creator = creator, mode = mode).also { it.status = status }

    @Test
    fun `createVote - creates vote, participants, options and sends invitations`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        val participant = VoteParticipant(voteId = vote.id, email = creator.email)

        every { userQueryService.findById(creator.id) } returns creator
        every { voteRepositoryPort.save(any()) } returns vote
        every { userQueryService.findAllByEmailIn(any()) } returns listOf(creator)
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
        verify { notificationPort.notifyVoteInvitation("p@test.com", vote) }
        verify(exactly = 0) { notificationPort.notifyVoteInvitation(creator.email, any()) }
    }

    @Test
    fun `getVote - throws when requester has no access`() {
        val creator = makeUser()
        val requester = makeUser(email = "other@test.com")
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
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
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, requester.email) } returns false

        assertThrows<ForbiddenException> {
            voteService.getHistory(vote.id, requester.id, requester.email)
        }
    }

    @Test
    fun `addParticipant - throws when requester is not the creator`() {
        val creator = makeUser()
        val otherUser = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote

        assertThrows<ForbiddenException> {
            voteService.addParticipant(vote.id, otherUser.id, "new@test.com")
        }
    }

    @Test
    fun `addParticipant - throws when vote is not PENDING`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.DRAWN)

        every { voteRepositoryPort.findById(vote.id) } returns vote

        assertThrows<IllegalStateException> {
            voteService.addParticipant(vote.id, creator.id, "new@test.com")
        }
    }

    @Test
    fun `addParticipant - throws when participant already exists`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, "dup@test.com") } returns true

        assertThrows<IllegalStateException> {
            voteService.addParticipant(vote.id, creator.id, "dup@test.com")
        }
    }

    @Test
    fun `addParticipant - saves participant and sends invitation`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        val participant = VoteParticipant(voteId = vote.id, email = "new@test.com")

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { participantRepositoryPort.existsByVoteIdAndEmail(vote.id, "new@test.com") } returns false
        every { userQueryService.findByEmail("new@test.com") } returns null
        every { participantRepositoryPort.save(any()) } returns participant

        voteService.addParticipant(vote.id, creator.id, "new@test.com")

        verify { participantRepositoryPort.save(any()) }
        verify { notificationPort.notifyVoteInvitation("new@test.com", vote) }
    }

    @Test
    fun `removeParticipant - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote

        assertThrows<ForbiddenException> {
            voteService.removeParticipant(vote.id, other.id, "p@test.com")
        }
    }

    @Test
    fun `removeParticipant - throws when vote is not PENDING`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.CLOSED)

        every { voteRepositoryPort.findById(vote.id) } returns vote

        assertThrows<IllegalStateException> {
            voteService.removeParticipant(vote.id, creator.id, "p@test.com")
        }
    }

    @Test
    fun `removeParticipant - deletes participant`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
        every { participantRepositoryPort.deleteByVoteIdAndEmail(vote.id, "p@test.com") } just Runs

        voteService.removeParticipant(vote.id, creator.id, "p@test.com")

        verify { participantRepositoryPort.deleteByVoteIdAndEmail(vote.id, "p@test.com") }
    }

    @Test
    fun `draw - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findByIdForDraw(vote.id) } returns vote

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

        every { voteRepositoryPort.findByIdForDraw(vote.id) } returns vote
        every { drawService.draw(vote) } returns drawResult
        every { participantRepositoryPort.findAllByVoteId(vote.id) } returns listOf(participant)

        val result = voteService.draw(vote.id, creator.id)

        assertEquals(drawResult.winnerEmail, result.winnerEmail)
        verify { drawService.draw(vote) }
        // afterCommit falls back to synchronous call in unit tests (no active transaction)
        verify { notificationPort.notifyDrawResult(vote, drawResult, listOf("winner@test.com")) }
    }

    @Test
    fun `closeVote - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote

        assertThrows<ForbiddenException> { voteService.closeVote(vote.id, other.id) }
    }

    @Test
    fun `closeVote - sets status to CLOSED`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
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

        assertThrows<IllegalStateException> { voteService.closeVote(vote.id, creator.id) }
    }

    @Test
    fun `deleteVote - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote

        assertThrows<ForbiddenException> { voteService.deleteVote(vote.id, other.id) }
    }

    @Test
    fun `deleteVote - deletes the vote`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepositoryPort.findById(vote.id) } returns vote
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

        assertThrows<ForbiddenException> { voteService.reopen(vote.id, other.id) }
    }

    @Test
    fun `reopen - delegates to DrawService`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.DRAWN)

        every { voteRepositoryPort.findById(vote.id) } returns vote
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
        every { voteOptionRepositoryPort.deleteByVoteIdAndId(vote.id, optionId) } just Runs

        voteService.removeOption(vote.id, creator.id, optionId)

        verify { voteOptionRepositoryPort.deleteByVoteIdAndId(vote.id, optionId) }
    }
}
