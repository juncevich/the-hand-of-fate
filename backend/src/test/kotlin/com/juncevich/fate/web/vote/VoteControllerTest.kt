package com.juncevich.fate.web.vote

import com.juncevich.fate.domain.vote.VoteMode
import com.juncevich.fate.domain.vote.VoteStatus
import com.juncevich.fate.security.AuthenticatedUser
import com.juncevich.fate.service.DrawResult
import com.juncevich.fate.service.VoteService
import com.juncevich.fate.web.common.ErrorHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class VoteControllerTest {

    private val voteService = mockk<VoteService>()
    private lateinit var mockMvc: MockMvc

    private val userId = UUID.randomUUID()
    private val voteId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(VoteController(voteService))
            .setControllerAdvice(ErrorHandler())
            .setCustomArgumentResolvers(
                AuthenticationPrincipalArgumentResolver(),
                PageableHandlerMethodArgumentResolver(),
            )
            .build()

        val principal = AuthenticatedUser(userId, "user@test.com")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun summaryDto(id: UUID = voteId) = VoteSummaryDto(
        id = id,
        title = "Test Vote",
        mode = VoteMode.SIMPLE,
        status = VoteStatus.PENDING,
        currentRound = 1,
        participantCount = 2,
        isCreator = true,
        createdAt = Instant.now(),
    )

    private fun detailDto(id: UUID = voteId) = VoteDetailDto(
        id = id,
        title = "Test Vote",
        description = null,
        mode = VoteMode.SIMPLE,
        status = VoteStatus.PENDING,
        currentRound = 1,
        participants = listOf(ParticipantDto("user@test.com", "User")),
        options = emptyList(),
        lastResult = null,
        isCreator = true,
        createdAt = Instant.now(),
    )

    // ── GET /votes ─────────────────────────────────────────────────────────────

    @Test
    fun `GET votes - returns page of summaries`() {
        val page = PageImpl(listOf(summaryDto()), PageRequest.of(0, 20), 1)
        every { voteService.listVotes(userId, "user@test.com", any()) } returns page

        mockMvc.get("/api/v1/votes").andExpect {
            status { isOk() }
            jsonPath("$.content[0].id") { value(voteId.toString()) }
            jsonPath("$.content[0].title") { value("Test Vote") }
        }
    }

    // ── POST /votes ────────────────────────────────────────────────────────────

    @Test
    fun `POST votes - valid request - returns 201`() {
        every { voteService.createVote(userId, any()) } returns detailDto()

        mockMvc.post("/api/v1/votes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Test Vote","mode":"SIMPLE","participantEmails":[]}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(voteId.toString()) }
            jsonPath("$.title") { value("Test Vote") }
        }
    }

    @Test
    fun `POST votes - blank title - returns 400`() {
        mockMvc.post("/api/v1/votes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"","mode":"SIMPLE","participantEmails":[]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.title") { exists() }
        }
    }

    // ── GET /votes/{id} ────────────────────────────────────────────────────────

    @Test
    fun `GET vote by id - returns vote detail`() {
        every { voteService.getVote(voteId, userId) } returns detailDto()

        mockMvc.get("/api/v1/votes/$voteId").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(voteId.toString()) }
            jsonPath("$.status") { value("PENDING") }
        }
    }

    // ── POST /votes/{id}/draw ──────────────────────────────────────────────────

    @Test
    fun `POST draw - returns draw result`() {
        every { voteService.draw(voteId, userId) } returns DrawResult(
            winnerEmail = "winner@test.com",
            winnerDisplayName = "Winner",
            winnerOptionTitle = null,
            round = 1,
            newRoundStarted = false,
        )

        mockMvc.post("/api/v1/votes/$voteId/draw").andExpect {
            status { isOk() }
            jsonPath("$.winnerEmail") { value("winner@test.com") }
            jsonPath("$.round") { value(1) }
            jsonPath("$.newRoundStarted") { value(false) }
        }

        verify { voteService.draw(voteId, userId) }
    }

    // ── DELETE /votes/{id} ─────────────────────────────────────────────────────

    @Test
    fun `DELETE vote - returns 204`() {
        every { voteService.deleteVote(voteId, userId) } returns Unit

        mockMvc.delete("/api/v1/votes/$voteId").andExpect {
            status { isNoContent() }
        }

        verify { voteService.deleteVote(voteId, userId) }
    }

    // ── POST /votes/{id}/participants ──────────────────────────────────────────

    @Test
    fun `POST participants - invalid email - returns 400`() {
        mockMvc.post("/api/v1/votes/$voteId/participants") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"not-an-email"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST participants - valid email - returns 204`() {
        every { voteService.addParticipant(voteId, userId, "new@test.com") } returns Unit

        mockMvc.post("/api/v1/votes/$voteId/participants") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"new@test.com"}"""
        }.andExpect {
            status { isNoContent() }
        }
    }

    // ── POST /votes/{id}/close ─────────────────────────────────────────────────

    @Test
    fun `POST close - returns 204`() {
        every { voteService.closeVote(voteId, userId) } returns Unit

        mockMvc.post("/api/v1/votes/$voteId/close").andExpect {
            status { isNoContent() }
        }
    }

    // ── POST /votes/{id}/reopen ────────────────────────────────────────────────

    @Test
    fun `POST reopen - returns 204`() {
        every { voteService.reopen(voteId, userId) } returns Unit

        mockMvc.post("/api/v1/votes/$voteId/reopen").andExpect {
            status { isNoContent() }
        }
    }

    // ── GET /votes/{id}/history ────────────────────────────────────────────────

    @Test
    fun `GET history - returns list`() {
        every { voteService.getHistory(voteId) } returns emptyList()

        mockMvc.get("/api/v1/votes/$voteId/history").andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
        }
    }
}
