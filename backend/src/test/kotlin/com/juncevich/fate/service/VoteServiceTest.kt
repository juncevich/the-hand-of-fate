package com.juncevich.fate.service

import com.juncevich.fate.domain.user.User
import com.juncevich.fate.domain.user.UserRepository
import com.juncevich.fate.domain.vote.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class VoteServiceTest {

    private val voteRepository = mockk<VoteRepository>()
    private val participantRepository = mockk<VoteParticipantRepository>()
    private val drawHistoryRepository = mockk<DrawHistoryRepository>()
    private val userRepository = mockk<UserRepository>()
    private val drawService = mockk<DrawService>()
    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val meterRegistry = mockk<MeterRegistry>()
    private val counter = mockk<Counter>(relaxed = true)

    private val voteService = VoteService(
        voteRepository, participantRepository, drawHistoryRepository,
        userRepository, drawService, notificationService, meterRegistry,
    )

    @BeforeEach
    fun setUp() {
        every { meterRegistry.counter(any<String>(), *anyVararg<String>()) } returns counter
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeUser(id: UUID = UUID.randomUUID(), email: String = "user@test.com") = User(
        email = email,
        passwordHash = "hash",
        displayName = "Test User",
    ).also { field ->
        // Set the id via reflection since it is val
        val idField = field.javaClass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(field, id)
    }

    private fun makeVote(
        id: UUID = UUID.randomUUID(),
        creator: User,
        mode: VoteMode = VoteMode.SIMPLE,
        status: VoteStatus = VoteStatus.PENDING,
    ) = Vote(title = "Test Vote", creator = creator, mode = mode)
        .also { v ->
            v.status = status
            val idField = v.javaClass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(v, id)
        }

    // ── addParticipant ────────────────────────────────────────────────────────

    @Test
    fun `addParticipant - throws when requester is not the creator`() {
        val creator = makeUser()
        val otherUser = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)

        assertThrows<IllegalStateException> {
            voteService.addParticipant(vote.id, otherUser.id, "new@test.com")
        }
    }

    @Test
    fun `addParticipant - throws when vote is not PENDING`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.DRAWN)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)

        assertThrows<IllegalStateException> {
            voteService.addParticipant(vote.id, creator.id, "new@test.com")
        }
    }

    @Test
    fun `addParticipant - throws when participant already exists`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)
        every { participantRepository.existsByVoteIdAndEmail(vote.id, "dup@test.com") } returns true

        assertThrows<IllegalStateException> {
            voteService.addParticipant(vote.id, creator.id, "dup@test.com")
        }
    }

    @Test
    fun `addParticipant - saves participant and sends invitation`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        val participant = VoteParticipant(vote = vote, email = "new@test.com")

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)
        every { participantRepository.existsByVoteIdAndEmail(vote.id, "new@test.com") } returns false
        every { userRepository.findByEmail("new@test.com") } returns null
        every { participantRepository.save(any()) } returns participant

        voteService.addParticipant(vote.id, creator.id, "new@test.com")

        verify { participantRepository.save(any()) }
        verify { notificationService.notifyVoteInvitation("new@test.com", vote) }
    }

    // ── removeParticipant ─────────────────────────────────────────────────────

    @Test
    fun `removeParticipant - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)

        assertThrows<IllegalStateException> {
            voteService.removeParticipant(vote.id, other.id, "p@test.com")
        }
    }

    @Test
    fun `removeParticipant - throws when vote is not PENDING`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.CLOSED)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)

        assertThrows<IllegalStateException> {
            voteService.removeParticipant(vote.id, creator.id, "p@test.com")
        }
    }

    @Test
    fun `removeParticipant - deletes participant`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)
        every { participantRepository.deleteByVoteIdAndEmail(vote.id, "p@test.com") } just Runs

        voteService.removeParticipant(vote.id, creator.id, "p@test.com")

        verify { participantRepository.deleteByVoteIdAndEmail(vote.id, "p@test.com") }
    }

    // ── draw ──────────────────────────────────────────────────────────────────

    @Test
    fun `draw - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)

        assertThrows<IllegalStateException> {
            voteService.draw(vote.id, other.id)
        }
    }

    @Test
    fun `draw - delegates to DrawService and notifies participants`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)
        val drawResult = DrawResult("winner@test.com", "Winner", 1, false)
        val participant = VoteParticipant(vote = vote, email = "winner@test.com")

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)
        every { drawService.draw(vote.id) } returns drawResult
        every { participantRepository.findAllByVoteId(vote.id) } returns listOf(participant)

        val result = voteService.draw(vote.id, creator.id)

        assertEquals(drawResult.winnerEmail, result.winnerEmail)
        verify { drawService.draw(vote.id) }
        verify { notificationService.notifyDrawResult(vote, drawResult, listOf("winner@test.com")) }
    }

    // ── closeVote ─────────────────────────────────────────────────────────────

    @Test
    fun `closeVote - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)

        assertThrows<IllegalStateException> { voteService.closeVote(vote.id, other.id) }
    }

    @Test
    fun `closeVote - sets status to CLOSED`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)
        every { voteRepository.save(any()) } returns vote

        voteService.closeVote(vote.id, creator.id)

        assertEquals(VoteStatus.CLOSED, vote.status)
        verify { voteRepository.save(vote) }
    }

    // ── deleteVote ────────────────────────────────────────────────────────────

    @Test
    fun `deleteVote - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)

        assertThrows<IllegalStateException> { voteService.deleteVote(vote.id, other.id) }
    }

    @Test
    fun `deleteVote - deletes the vote`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)
        every { voteRepository.delete(vote) } just Runs

        voteService.deleteVote(vote.id, creator.id)

        verify { voteRepository.delete(vote) }
    }

    // ── reopen ────────────────────────────────────────────────────────────────

    @Test
    fun `reopen - throws when requester is not the creator`() {
        val creator = makeUser()
        val other = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.DRAWN)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)

        assertThrows<IllegalStateException> { voteService.reopen(vote.id, other.id) }
    }

    @Test
    fun `reopen - delegates to DrawService`() {
        val creator = makeUser()
        val vote = makeVote(creator = creator, status = VoteStatus.DRAWN)

        every { voteRepository.findById(vote.id) } returns Optional.of(vote)
        every { drawService.reopen(vote.id) } just Runs

        voteService.reopen(vote.id, creator.id)

        verify { drawService.reopen(vote.id) }
    }
}
