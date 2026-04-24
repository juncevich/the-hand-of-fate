package com.juncevich.fate.grpc

import com.juncevich.fate.domain.user.User
import com.juncevich.fate.domain.user.UserRepository
import com.juncevich.fate.domain.vote.DrawHistory
import com.juncevich.fate.domain.vote.DrawHistoryRepository
import com.juncevich.fate.domain.vote.Vote
import com.juncevich.fate.domain.vote.VoteMode as DomainVoteMode
import com.juncevich.fate.domain.vote.VoteParticipantRepository
import com.juncevich.fate.domain.vote.VoteRepository
import com.juncevich.fate.domain.vote.VoteStatus as DomainVoteStatus
import com.juncevich.fate.service.TelegramLinkService
import com.juncevich.fate.service.VoteService
import com.juncevich.fate.web.vote.ParticipantDto
import com.juncevich.fate.web.vote.VoteDetailDto
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class FateGrpcServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val voteRepository = mockk<VoteRepository>()
    private val participantRepository = mockk<VoteParticipantRepository>()
    private val drawHistoryRepository = mockk<DrawHistoryRepository>()
    private val telegramLinkService = mockk<TelegramLinkService>()
    private val voteService = mockk<VoteService>()

    private val service = FateGrpcService(
        userRepository = userRepository,
        voteRepository = voteRepository,
        participantRepository = participantRepository,
        drawHistoryRepository = drawHistoryRepository,
        telegramLinkService = telegramLinkService,
        voteService = voteService,
    )

    @Test
    fun `createVote creates vote for linked telegram user`() = runBlocking {
        val user = user(telegramId = 42)
        val voteId = UUID.randomUUID()
        val createdVote = VoteDetailDto(
            id = voteId,
            title = "Lunch",
            description = null,
            mode = DomainVoteMode.FAIR_ROTATION,
            status = DomainVoteStatus.PENDING,
            currentRound = 1,
            participants = listOf(
                ParticipantDto(user.email, user.displayName),
                ParticipantDto("friend@example.com", null),
            ),
            lastResult = null,
            isCreator = true,
            createdAt = Instant.parse("2026-04-25T00:00:00Z"),
        )

        every { userRepository.findByTelegramId(42) } returns user
        every {
            voteService.createVote(
                creatorId = user.id,
                request = match {
                    it.title == "Lunch" &&
                        it.mode == DomainVoteMode.FAIR_ROTATION &&
                        it.participantEmails == listOf("friend@example.com")
                },
            )
        } returns createdVote

        val response = service.createVote(
            CreateVoteRequest.newBuilder()
                .setTelegramId(42)
                .setTitle(" Lunch ")
                .setMode(VoteMode.VOTE_MODE_FAIR_ROTATION)
                .addParticipantEmails("friend@example.com")
                .build()
        )

        assertTrue(response.success)
        assertEquals(voteId.toString(), response.vote.voteId)
        assertEquals(VoteMode.VOTE_MODE_FAIR_ROTATION, response.vote.mode)
        assertEquals(2, response.vote.participantsCount)
    }

    @Test
    fun `getVoteHistory returns backend history for accessible vote`() = runBlocking {
        val user = user(telegramId = 42)
        val vote = vote(creator = user)
        val history = listOf(
            DrawHistory(
                vote = vote,
                winnerEmail = "friend@example.com",
                winnerDisplayName = "Friend",
                round = 2,
                drawnAt = Instant.parse("2026-04-25T00:00:00Z"),
            ),
        )

        every { userRepository.findByTelegramId(42) } returns user
        every { voteRepository.findById(vote.id) } returns Optional.of(vote)
        every { voteService.getHistory(vote.id) } returns history

        val response = service.getVoteHistory(
            GetVoteHistoryRequest.newBuilder()
                .setTelegramId(42)
                .setVoteId(vote.id.toString())
                .build()
        )

        assertEquals(1, response.resultsCount)
        assertEquals("friend@example.com", response.getResults(0).winnerEmail)
        assertEquals(2, response.getResults(0).round)
    }

    private fun user(telegramId: Long): User = User(
        email = "owner@example.com",
        passwordHash = "hash",
        displayName = "Owner",
        telegramId = telegramId,
    )

    private fun vote(creator: User): Vote = Vote(
        title = "Lunch",
        creator = creator,
        mode = DomainVoteMode.SIMPLE,
    )
}
