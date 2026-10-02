package com.juncevich.fate.grpc

import com.google.rpc.ErrorInfo
import com.juncevich.fate.auth.TelegramLinkService
import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.UserQueryService
import com.juncevich.fate.auth.toProfile
import com.juncevich.fate.grpc.FateProto.*
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.vote.*
import com.juncevich.fate.vote.internal.domain.Vote
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.UUID
import com.juncevich.fate.vote.VoteMode as DomainVoteMode
import com.juncevich.fate.vote.VoteStatus as DomainVoteStatus

class FateGrpcServiceTest {
    private val userQueryService = mockk<UserQueryService>()
    private val telegramLinkService = mockk<TelegramLinkService>()
    private val voteService = mockk<VoteService>()

    private val service =
        FateGrpcService(
            userQueryService = userQueryService,
            telegramLinkService = telegramLinkService,
            voteService = voteService
        )

    @Test
    fun `createVote creates vote for linked telegram user`() =
        runBlocking {
            val user = user(telegramId = 42)
            val voteId = UUID.randomUUID()
            val createdVote =
                VoteDetailDto(
                    id = voteId,
                    title = "Lunch",
                    description = null,
                    mode = DomainVoteMode.FAIR_ROTATION,
                    status = DomainVoteStatus.PENDING,
                    currentRound = 1,
                    participants =
                        listOf(
                            ParticipantDto(user.email, user.displayName),
                            ParticipantDto("friend@example.com", null)
                        ),
                    options = emptyList(),
                    lastResult = null,
                    isCreator = true,
                    createdAt = Instant.parse("2026-04-25T00:00:00Z")
                )

            every { userQueryService.findByTelegramId(42) } returns user
            every {
                voteService.createVote(
                    creatorId = user.id,
                    request =
                        match {
                            it.title == "Lunch" &&
                                it.mode == DomainVoteMode.FAIR_ROTATION &&
                                it.participantEmails == listOf("friend@example.com")
                        }
                )
            } returns createdVote

            val response =
                service.createVote(
                    CreateVoteRequest
                        .newBuilder()
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
    fun `getVoteHistory returns backend history for accessible vote`() =
        runBlocking {
            val user = user(telegramId = 42)
            val vote = vote(creator = user)
            val history =
                listOf(
                    DrawHistoryDto(
                        id = UUID.randomUUID(),
                        winnerEmail = "friend@example.com",
                        winnerDisplayName = "Friend",
                        winnerOptionTitle = null,
                        round = 2,
                        drawnAt = Instant.parse("2026-04-25T00:00:00Z")
                    )
                )

            every { userQueryService.findByTelegramId(42) } returns user
            every { voteService.getHistory(vote.id, user.id, user.email, any()) } returns
                org.springframework.data.domain
                    .PageImpl(history)

            val response =
                service.getVoteHistory(
                    GetVoteHistoryRequest
                        .newBuilder()
                        .setTelegramId(42)
                        .setVoteId(vote.id.toString())
                        .build()
                )

            assertEquals(1, response.resultsCount)
            assertEquals("friend@example.com", response.getResults(0).winnerEmail)
            assertEquals(2, response.getResults(0).round)
        }

    // ── linkTelegramAccount ──────────────────────────────────────────────────

    @Test
    fun `linkTelegramAccount returns display name on success`() =
        runBlocking {
            val user = user(telegramId = 42)
            every { telegramLinkService.linkAccount("tok", 42, "tg") } returns user

            val response =
                service.linkTelegramAccount(
                    LinkTelegramAccountRequest
                        .newBuilder()
                        .setLinkToken("tok")
                        .setTelegramId(42)
                        .setTelegramName("tg")
                        .build()
                )

            assertTrue(response.success)
            assertEquals("Owner", response.displayName)
        }

    @Test
    fun `linkTelegramAccount maps domain errors to unsuccessful response`() =
        runBlocking {
            every { telegramLinkService.linkAccount(any(), any(), any()) } throws
                BadRequestException("Link token expired")

            val response = service.linkTelegramAccount(linkRequest())

            assertFalse(response.success)
            assertEquals("Link token expired", response.message)
        }

    @Test
    fun `linkTelegramAccount maps unexpected errors to INTERNAL`() {
        every { telegramLinkService.linkAccount(any(), any(), any()) } throws RuntimeException("db down")

        val ex = assertThrows<StatusRuntimeException> { runBlocking { service.linkTelegramAccount(linkRequest()) } }

        assertEquals(Status.Code.INTERNAL, ex.status.code)
        assertEquals("Unexpected error", ex.status.description)
    }

    // ── unlinkTelegramAccount ────────────────────────────────────────────────

    @Test
    fun `unlinkTelegramAccount succeeds`() =
        runBlocking {
            every { telegramLinkService.unlinkAccount(42) } just runs

            val response = service.unlinkTelegramAccount(unlinkRequest())

            assertTrue(response.success)
            assertEquals("Account unlinked.", response.message)
            verify { telegramLinkService.unlinkAccount(42) }
        }

    @Test
    fun `unlinkTelegramAccount maps domain errors to unsuccessful response`() =
        runBlocking {
            every { telegramLinkService.unlinkAccount(42) } throws NotFoundException("Not linked")

            val response = service.unlinkTelegramAccount(unlinkRequest())

            assertFalse(response.success)
            assertEquals("Not linked", response.message)
        }

    @Test
    fun `unlinkTelegramAccount maps unexpected errors to INTERNAL`() {
        every { telegramLinkService.unlinkAccount(42) } throws RuntimeException("boom")

        val ex = assertThrows<StatusRuntimeException> { runBlocking { service.unlinkTelegramAccount(unlinkRequest()) } }

        assertEquals(Status.Code.INTERNAL, ex.status.code)
    }

    // ── getMyVotes ───────────────────────────────────────────────────────────

    @Test
    fun `getMyVotes aggregates all pages`() =
        runBlocking {
            val user = user(telegramId = 42)
            every { userQueryService.findByTelegramId(42) } returns user
            val first = summary("First", DomainVoteStatus.CLOSED)
            val second = summary("Second", DomainVoteStatus.DRAWN)
            every { voteService.listVotes(user.id, user.email, PageRequest.of(0, 50)) } returns
                PageImpl(listOf(first), PageRequest.of(0, 50), 51)
            every { voteService.listVotes(user.id, user.email, PageRequest.of(1, 50)) } returns
                PageImpl(listOf(second), PageRequest.of(1, 50), 51)

            val response = service.getMyVotes(GetMyVotesRequest.newBuilder().setTelegramId(42).build())

            assertEquals(listOf("First", "Second"), response.votesList.map { it.title })
            assertEquals(VoteStatus.VOTE_STATUS_CLOSED, response.getVotes(0).status)
            assertEquals(VoteStatus.VOTE_STATUS_DRAWN, response.getVotes(1).status)
        }

    @Test
    fun `getMyVotes stops after the page limit`() =
        runBlocking {
            val user = user(telegramId = 42)
            every { userQueryService.findByTelegramId(42) } returns user
            every { voteService.listVotes(user.id, user.email, any()) } answers {
                val pageable = thirdArg<PageRequest>()
                PageImpl(listOf(summary("V${pageable.pageNumber}")), pageable, 10_000)
            }

            val response = service.getMyVotes(GetMyVotesRequest.newBuilder().setTelegramId(42).build())

            assertEquals(20, response.votesCount)
            verify(exactly = 20) { voteService.listVotes(user.id, user.email, any()) }
        }

    @Test
    fun `getMyVotes rejects unlinked telegram account`() {
        every { userQueryService.findByTelegramId(7) } returns null

        val ex =
            assertThrows<StatusRuntimeException> {
                runBlocking { service.getMyVotes(GetMyVotesRequest.newBuilder().setTelegramId(7).build()) }
            }

        assertEquals(Status.Code.NOT_FOUND, ex.status.code)
        assertEquals("Telegram account not linked", ex.status.description)
        val info =
            checkNotNull(StatusProto.fromThrowable(ex))
                .detailsList
                .single()
                .unpack(ErrorInfo::class.java)
        assertEquals("TELEGRAM_NOT_LINKED", info.reason)
        assertEquals("fate.v1", info.domain)
    }

    // ── createVote ───────────────────────────────────────────────────────────

    @Test
    fun `createVote rejects blank title`() {
        every { userQueryService.findByTelegramId(42) } returns user(telegramId = 42)

        val ex =
            assertThrows<StatusRuntimeException> {
                runBlocking {
                    service.createVote(
                        CreateVoteRequest
                            .newBuilder()
                            .setTelegramId(42)
                            .setTitle("   ")
                            .build()
                    )
                }
            }

        assertEquals(Status.Code.INVALID_ARGUMENT, ex.status.code)
    }

    @Test
    fun `createVote delegates input validation and normalization to the service`() =
        runBlocking {
            val user = user(telegramId = 42)
            every { userQueryService.findByTelegramId(42) } returns user
            every {
                voteService.createVote(
                    creatorId = user.id,
                    request =
                        match {
                            it.description == "Friday" &&
                                it.mode == DomainVoteMode.SIMPLE &&
                                it.options == listOf(" Pizza ", "Sushi", "Pizza", "") &&
                                it.participantEmails == listOf(" ", "")
                        }
                )
            } returns detail(options = listOf(VoteOptionDto(UUID.randomUUID(), "Pizza")))

            val response =
                service.createVote(
                    CreateVoteRequest
                        .newBuilder()
                        .setTelegramId(42)
                        .setTitle("Lunch")
                        .setDescription("Friday")
                        .addAllParticipantEmails(listOf(" ", ""))
                        .addAllOptions(listOf(" Pizza ", "Sushi", "Pizza", ""))
                        .build()
                )

            assertTrue(response.success)
            assertEquals("Pizza", response.vote.getOptions(0).title)
            assertEquals(VoteMode.VOTE_MODE_SIMPLE, response.vote.mode)
        }

    @Test
    fun `createVote maps domain errors to unsuccessful response`() =
        runBlocking {
            every { userQueryService.findByTelegramId(42) } returns user(telegramId = 42)
            every { voteService.createVote(any(), any()) } throws BadRequestException("Too many options")

            val response =
                service.createVote(
                    CreateVoteRequest
                        .newBuilder()
                        .setTelegramId(42)
                        .setTitle("Lunch")
                        .build()
                )

            assertFalse(response.success)
            assertEquals("Too many options", response.message)
        }

    @Test
    fun `createVote maps unexpected errors to INTERNAL`() {
        every { userQueryService.findByTelegramId(42) } returns user(telegramId = 42)
        every { voteService.createVote(any(), any()) } throws RuntimeException("boom")

        val ex =
            assertThrows<StatusRuntimeException> {
                runBlocking {
                    service.createVote(
                        CreateVoteRequest
                            .newBuilder()
                            .setTelegramId(42)
                            .setTitle("Lunch")
                            .build()
                    )
                }
            }

        assertEquals(Status.Code.INTERNAL, ex.status.code)
    }

    // ── getVoteDetails ───────────────────────────────────────────────────────

    @Test
    fun `getVoteDetails maps vote with last result`() =
        runBlocking {
            val user = user(telegramId = 42)
            val last = historyDto(optionTitle = "Pizza")
            val dto = detail(description = "desc", lastResult = last)
            every { userQueryService.findByTelegramId(42) } returns user
            every { voteService.getVote(dto.id, user.id, user.email) } returns dto

            val response = service.getVoteDetails(detailsRequest(dto.id.toString()))

            assertEquals("desc", response.description)
            assertTrue(response.hasLastResult())
            assertEquals("Pizza", response.lastResult.winnerOptionTitle)
            assertEquals("", response.lastResult.winnerEmail)
            assertEquals("2026-04-25T00:00:00Z", response.lastResult.drawnAt)
        }

    @Test
    fun `getVoteDetails rejects malformed vote id`() {
        every { userQueryService.findByTelegramId(42) } returns user(telegramId = 42)

        val ex = assertThrows<StatusRuntimeException> { runBlocking { service.getVoteDetails(detailsRequest("nope")) } }

        assertEquals(Status.Code.INVALID_ARGUMENT, ex.status.code)
        assertEquals("Invalid vote id", ex.status.description)
    }

    @Test
    fun `getVoteDetails maps missing vote to NOT_FOUND`() {
        assertReadError(NotFoundException("Vote not found"), Status.Code.NOT_FOUND)
    }

    @Test
    fun `getVoteDetails maps access denial to PERMISSION_DENIED`() {
        assertReadError(ForbiddenException("Access denied"), Status.Code.PERMISSION_DENIED)
    }

    @Test
    fun `getVoteDetails maps each domain error category to its status`() {
        assertReadError(BadRequestException("Bad input"), Status.Code.INVALID_ARGUMENT)
        assertReadError(NotFoundException("Vote not found"), Status.Code.NOT_FOUND)
        assertReadError(ConflictException("Vote is closed"), Status.Code.FAILED_PRECONDITION)
    }

    @Test
    fun `getVoteDetails maps unexpected errors to INTERNAL`() {
        assertReadError(RuntimeException("boom"), Status.Code.INTERNAL)
        assertReadError(IllegalStateException("Corrupt row 42"), Status.Code.INTERNAL)
        assertReadError(NoSuchElementException("List is empty"), Status.Code.INTERNAL)
    }

    // ── drawVote ─────────────────────────────────────────────────────────────

    @Test
    fun `drawVote returns winner`() =
        runBlocking {
            val user = user(telegramId = 42)
            val voteId = UUID.randomUUID()
            every { userQueryService.findByTelegramId(42) } returns user
            every { voteService.draw(voteId, user.id) } returns
                DrawResult(
                    winnerEmail = "friend@example.com",
                    winnerDisplayName = "Friend",
                    winnerOptionTitle = null,
                    round = 3,
                    newRoundStarted = true
                )

            val response = service.drawVote(drawRequest(voteId.toString()))

            assertTrue(response.success)
            assertEquals("friend@example.com", response.winnerEmail)
            assertEquals("", response.winnerOptionTitle)
            assertEquals(3, response.round)
            assertTrue(response.newRoundStarted)
            assertEquals("✦ The Hand of Fate has chosen: Friend", response.message)
        }

    @Test
    fun `drawVote maps domain errors to unsuccessful response`() =
        runBlocking {
            val voteId = UUID.randomUUID()
            every { userQueryService.findByTelegramId(42) } returns user(telegramId = 42)
            every { voteService.draw(voteId, any()) } throws ForbiddenException("Only the creator can draw")

            val response = service.drawVote(drawRequest(voteId.toString()))

            assertFalse(response.success)
            assertEquals("Only the creator can draw", response.message)
        }

    @Test
    fun `drawVote maps unexpected errors to INTERNAL`() {
        val voteId = UUID.randomUUID()
        every { userQueryService.findByTelegramId(42) } returns user(telegramId = 42)
        every { voteService.draw(voteId, any()) } throws RuntimeException("boom")

        val ex = assertThrows<StatusRuntimeException> { runBlocking { service.drawVote(drawRequest(voteId.toString())) } }

        assertEquals(Status.Code.INTERNAL, ex.status.code)
    }

    // ── getLastDrawResult ────────────────────────────────────────────────────

    @Test
    fun `getLastDrawResult returns hasResult false when nothing drawn`() =
        runBlocking {
            val user = user(telegramId = 42)
            val voteId = UUID.randomUUID()
            every { userQueryService.findByTelegramId(42) } returns user
            every { voteService.getLastResult(voteId, user.id, user.email) } returns null

            val response = service.getLastDrawResult(lastResultRequest(voteId.toString()))

            assertFalse(response.hasResult)
        }

    @Test
    fun `getLastDrawResult returns latest result`() =
        runBlocking {
            val user = user(telegramId = 42)
            val voteId = UUID.randomUUID()
            every { userQueryService.findByTelegramId(42) } returns user
            every { voteService.getLastResult(voteId, user.id, user.email) } returns
                historyDto(email = "friend@example.com", displayName = "Friend")

            val response = service.getLastDrawResult(lastResultRequest(voteId.toString()))

            assertTrue(response.hasResult)
            assertEquals("friend@example.com", response.result.winnerEmail)
            assertEquals("Friend", response.result.winnerDisplayName)
            assertEquals(1, response.result.round)
        }

    @Test
    fun `getLastDrawResult maps missing vote to NOT_FOUND`() {
        val voteId = UUID.randomUUID()
        every { userQueryService.findByTelegramId(42) } returns user(telegramId = 42)
        every { voteService.getLastResult(voteId, any(), any()) } throws NotFoundException("Vote not found")

        val ex =
            assertThrows<StatusRuntimeException> {
                runBlocking { service.getLastDrawResult(lastResultRequest(voteId.toString())) }
            }

        assertEquals(Status.Code.NOT_FOUND, ex.status.code)
        assertEquals("Vote not found", ex.status.description)
    }

    @Test
    fun `getVoteHistory maps access denial to PERMISSION_DENIED`() {
        val voteId = UUID.randomUUID()
        every { userQueryService.findByTelegramId(42) } returns user(telegramId = 42)
        every { voteService.getHistory(voteId, any(), any(), any()) } throws ForbiddenException("Access denied")

        val ex =
            assertThrows<StatusRuntimeException> {
                runBlocking {
                    service.getVoteHistory(
                        GetVoteHistoryRequest
                            .newBuilder()
                            .setTelegramId(42)
                            .setVoteId(voteId.toString())
                            .build()
                    )
                }
            }

        assertEquals(Status.Code.PERMISSION_DENIED, ex.status.code)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun assertReadError(
        error: Throwable,
        expected: Status.Code,
    ) {
        val voteId = UUID.randomUUID()
        every { userQueryService.findByTelegramId(42) } returns user(telegramId = 42)
        every { voteService.getVote(voteId, any(), any()) } throws error

        val ex =
            assertThrows<StatusRuntimeException> {
                runBlocking { service.getVoteDetails(detailsRequest(voteId.toString())) }
            }

        assertEquals(expected, ex.status.code)
    }

    private fun linkRequest() =
        LinkTelegramAccountRequest
            .newBuilder()
            .setLinkToken("tok")
            .setTelegramId(42)
            .build()

    private fun unlinkRequest() = UnlinkTelegramAccountRequest.newBuilder().setTelegramId(42).build()

    private fun detailsRequest(voteId: String) =
        GetVoteDetailsRequest
            .newBuilder()
            .setTelegramId(42)
            .setVoteId(voteId)
            .build()

    private fun drawRequest(voteId: String) =
        DrawVoteRequest
            .newBuilder()
            .setTelegramId(42)
            .setVoteId(voteId)
            .build()

    private fun lastResultRequest(voteId: String) =
        GetLastDrawResultRequest
            .newBuilder()
            .setTelegramId(42)
            .setVoteId(voteId)
            .build()

    private fun summary(
        title: String,
        status: DomainVoteStatus = DomainVoteStatus.PENDING,
    ) = VoteSummaryDto(
        id = UUID.randomUUID(),
        title = title,
        mode = DomainVoteMode.SIMPLE,
        status = status,
        currentRound = 1,
        participantCount = 2,
        isCreator = true,
        createdAt = Instant.parse("2026-04-25T00:00:00Z")
    )

    private fun detail(
        description: String? = null,
        options: List<VoteOptionDto> = emptyList(),
        lastResult: DrawHistoryDto? = null,
    ) = VoteDetailDto(
        id = UUID.randomUUID(),
        title = "Lunch",
        description = description,
        mode = DomainVoteMode.SIMPLE,
        status = DomainVoteStatus.DRAWN,
        currentRound = 1,
        participants = listOf(ParticipantDto("friend@example.com", null)),
        options = options,
        lastResult = lastResult,
        isCreator = true,
        createdAt = Instant.parse("2026-04-25T00:00:00Z")
    )

    @Test
    fun `history supports explicit pages and returns total counts`() =
        runBlocking {
            val user = user(telegramId = 42)
            val voteId = UUID.randomUUID()
            val pageable = PageRequest.of(2, 10)
            every { userQueryService.findByTelegramId(42) } returns user
            every { voteService.getHistory(voteId, user.id, user.email, pageable) } returns PageImpl(List(5) { historyDto() }, pageable, 25)
            val response =
                service.getVoteHistory(
                    GetVoteHistoryRequest
                        .newBuilder()
                        .setVoteId(voteId.toString())
                        .setTelegramId(42)
                        .setPage(2)
                        .setPageSize(10)
                        .build()
                )
            assertEquals(25L, response.totalElements)
            assertEquals(3, response.totalPages)
            assertEquals(5, response.resultsCount)
        }

    @Test
    fun `history rejects negative pages and invalid page sizes`() =
        runBlocking {
            val user = user(telegramId = 42)
            every { userQueryService.findByTelegramId(42) } returns user
            for ((page, size) in listOf(-1 to 20, 0 to -1, 0 to 101)) {
                val exception =
                    assertThrows<StatusRuntimeException> {
                        service.getVoteHistory(
                            GetVoteHistoryRequest
                                .newBuilder()
                                .setVoteId(UUID.randomUUID().toString())
                                .setTelegramId(42)
                                .setPage(page)
                                .setPageSize(size)
                                .build()
                        )
                    }
                assertEquals(Status.Code.INVALID_ARGUMENT, exception.status.code)
            }
            verify(exactly = 0) { voteService.getHistory(any(), any(), any(), any()) }
        }

    @Test
    fun `createVote reports service validation failures without an internal transport error`() =
        runBlocking {
            every { userQueryService.findByTelegramId(42) } returns user(telegramId = 42)
            every { voteService.createVote(any(), any()) } throws
                com.juncevich.fate.shared
                    .BadRequestException("Invalid option title")
            val response =
                service.createVote(
                    CreateVoteRequest
                        .newBuilder()
                        .setTelegramId(42)
                        .setTitle("Vote")
                        .addOptions(" ")
                        .build()
                )
            assertFalse(response.success)
            assertEquals("Invalid option title", response.message)
        }

    private fun historyDto(
        email: String? = null,
        displayName: String? = null,
        optionTitle: String? = null,
    ) = DrawHistoryDto(
        id = UUID.randomUUID(),
        winnerEmail = email,
        winnerDisplayName = displayName,
        winnerOptionTitle = optionTitle,
        round = 1,
        drawnAt = Instant.parse("2026-04-25T00:00:00Z")
    )

    private fun user(telegramId: Long): User =
        User(
            email = "owner@example.com",
            passwordHash = "hash",
            displayName = "Owner",
            telegramId = telegramId
        )

    private fun vote(creator: User): Vote =
        Vote(
            title = "Lunch",
            creator = creator.toProfile(),
            mode = DomainVoteMode.SIMPLE
        )
}
