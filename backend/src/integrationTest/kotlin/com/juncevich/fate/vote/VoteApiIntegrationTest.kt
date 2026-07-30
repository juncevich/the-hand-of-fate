package com.juncevich.fate.vote

import com.juncevich.fate.AbstractApiIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

class VoteApiIntegrationTest : AbstractApiIntegrationTest() {
    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createUser(): Pair<String, String> {
        val email = "vote-user-${UUID.randomUUID()}@test.com"
        val token = registerAndGetToken(email)
        return token to email
    }

    private fun createSimpleVote(
        token: String,
        title: String = "Test Vote",
        participants: List<String> = emptyList(),
    ): String {
        val participantsJson = participants.joinToString(",") { "\"$it\"" }
        val result =
            mockMvc
                .post("/api/v1/votes") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"title":"$title","mode":"SIMPLE","participantEmails":[$participantsJson]}"""
                }.andReturn()
        return parse(result.response.contentAsString)["id"].asText()
    }

    private fun createFairVote(
        token: String,
        title: String = "Fair Vote",
        participants: List<String> = emptyList(),
        options: List<String> = emptyList(),
    ): String {
        val participantsJson = participants.joinToString(",") { "\"$it\"" }
        val optionsJson = options.joinToString(",") { "\"$it\"" }
        val result =
            mockMvc
                .post("/api/v1/votes") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"title":"$title","mode":"FAIR_ROTATION",
                        |"participantEmails":[$participantsJson],
                        |"options":[$optionsJson]}
                        """.trimMargin()
                }.andReturn()
        return parse(result.response.contentAsString)["id"].asText()
    }

    private fun draw(
        token: String,
        voteId: String,
    ): String {
        val result =
            mockMvc
                .post("/api/v1/votes/$voteId/draw") {
                    header("Authorization", "Bearer $token")
                }.andReturn()
        return result.response.contentAsString
    }

    private fun reopen(
        token: String,
        voteId: String,
    ) {
        mockMvc
            .post("/api/v1/votes/$voteId/reopen") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNoContent() } }
    }

    // ── Create vote ───────────────────────────────────────────────────────────

    @Test
    fun `POST votes - SIMPLE vote - returns 201 with vote details`() {
        val (token, email) = createUser()

        mockMvc
            .post("/api/v1/votes") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"My Vote","mode":"SIMPLE","participantEmails":[]}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { isString() }
                jsonPath("$.title") { value("My Vote") }
                jsonPath("$.mode") { value("SIMPLE") }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.currentRound") { value(1) }
                jsonPath("$.isCreator") { value(true) }
                jsonPath("$.participants[0].email") { value(email) }
            }
    }

    @Test
    fun `POST votes - FAIR_ROTATION vote with options - returns 201`() {
        val (token) = createUser()

        mockMvc
            .post("/api/v1/votes") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"title":"Options Vote","mode":"FAIR_ROTATION",
                    |"participantEmails":[],
                    |"options":["Task A","Task B","Task C"]}
                    """.trimMargin()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.mode") { value("FAIR_ROTATION") }
                jsonPath("$.options.length()") { value(3) }
                jsonPath("$.options[0].title") { value("Task A") }
            }
    }

    @Test
    fun `POST votes - blank title - returns 400`() {
        val (token) = createUser()

        mockMvc
            .post("/api/v1/votes") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"   ","mode":"SIMPLE","participantEmails":[]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.title") { exists() }
            }
    }

    // ── List votes ────────────────────────────────────────────────────────────

    @Test
    fun `GET votes - returns paginated list of user votes`() {
        val (token) = createUser()
        createSimpleVote(token, "Vote One")
        createSimpleVote(token, "Vote Two")

        mockMvc
            .get("/api/v1/votes") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.totalElements") { value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)) }
                jsonPath("$.content[0].title") { isString() }
                jsonPath("$.content[0].participantCount") { isNumber() }
                jsonPath("$.content[0].isCreator") { value(true) }
            }
    }

    @Test
    fun `GET votes - participant can see vote in list`() {
        val (creatorToken, creatorEmail) = createUser()
        val (participantToken, participantEmail) = createUser()

        createSimpleVote(creatorToken, "Shared Vote", listOf(participantEmail))

        val result =
            mockMvc
                .get("/api/v1/votes") {
                    header("Authorization", "Bearer $participantToken")
                }.andReturn()

        val content = parse(result.response.contentAsString)["content"]
        val sharedVote = content.find { it["title"].asText() == "Shared Vote" }
        assertNotNull(sharedVote, "Participant should see the vote they were invited to")
        assertEquals(false, sharedVote!!["isCreator"].asBoolean())
    }

    // ── Get vote ──────────────────────────────────────────────────────────────

    @Test
    fun `GET votes by id - creator - returns 200 with full details`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token, "Detail Vote")

        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(voteId) }
                jsonPath("$.title") { value("Detail Vote") }
                jsonPath("$.isCreator") { value(true) }
                jsonPath("$.lastResult") { doesNotExist() }
            }
    }

    @Test
    fun `GET votes by id - participant - returns 200`() {
        val (creatorToken) = createUser()
        val (participantToken, participantEmail) = createUser()
        val voteId = createSimpleVote(creatorToken, "Participant View", listOf(participantEmail))

        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $participantToken")
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(voteId) }
                jsonPath("$.isCreator") { value(false) }
            }
    }

    @Test
    fun `GET votes by id - non-participant - returns 403`() {
        val (creatorToken) = createUser()
        val (strangerToken) = createUser()
        val voteId = createSimpleVote(creatorToken, "Private Vote")

        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $strangerToken")
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `GET votes by id - not found - returns 404`() {
        val (token) = createUser()

        mockMvc
            .get("/api/v1/votes/${UUID.randomUUID()}") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNotFound() } }
    }

    // ── Delete vote ───────────────────────────────────────────────────────────

    @Test
    fun `DELETE votes by id - creator - returns 204`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        mockMvc
            .delete("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNoContent() } }

        // Vote should no longer exist
        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE votes by id - non-creator - returns 403`() {
        val (creatorToken) = createUser()
        val (otherToken, otherEmail) = createUser()
        val voteId = createSimpleVote(creatorToken, "Protected Vote", listOf(otherEmail))

        mockMvc
            .delete("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $otherToken")
            }.andExpect { status { isForbidden() } }
    }

    // ── Participants ──────────────────────────────────────────────────────────

    @Test
    fun `POST votes participants - add new email - returns 204`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)
        val newEmail = "new-participant-${UUID.randomUUID()}@test.com"

        mockMvc
            .post("/api/v1/votes/$voteId/participants") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$newEmail"}"""
            }.andExpect { status { isNoContent() } }

        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                jsonPath("$.participants[?(@.email == '$newEmail')]") { exists() }
            }
    }

    @Test
    fun `POST votes participants - duplicate email - returns 409`() {
        val (token) = createUser()
        val extra = "dup-part-${UUID.randomUUID()}@test.com"
        val voteId = createSimpleVote(token, participants = listOf(extra))

        mockMvc
            .post("/api/v1/votes/$voteId/participants") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$extra"}"""
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `DELETE votes participants email - removes participant - returns 204`() {
        val (token) = createUser()
        val extra = "remove-${UUID.randomUUID()}@test.com"
        val voteId = createSimpleVote(token, participants = listOf(extra))

        mockMvc
            .delete("/api/v1/votes/$voteId/participants/$extra") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNoContent() } }

        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                jsonPath("$.participants[?(@.email == '$extra')]") { doesNotExist() }
            }
    }

    // ── Options ───────────────────────────────────────────────────────────────

    @Test
    fun `POST votes options - add option - returns 204`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        mockMvc
            .post("/api/v1/votes/$voteId/options") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"Option Alpha"}"""
            }.andExpect { status { isNoContent() } }

        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                jsonPath("$.options[?(@.title == 'Option Alpha')]") { exists() }
            }
    }

    @Test
    fun `DELETE votes options by id - removes option - returns 204`() {
        val (token) = createUser()
        val voteId = createFairVote(token, options = listOf("Keep", "Remove"))

        val detailResult =
            mockMvc
                .get("/api/v1/votes/$voteId") {
                    header("Authorization", "Bearer $token")
                }.andReturn()

        val options = parse(detailResult.response.contentAsString)["options"]
        val optionToRemove = options.find { it["title"].asText() == "Remove" }!!
        val optionId = optionToRemove["id"].asText()

        mockMvc
            .delete("/api/v1/votes/$voteId/options/$optionId") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNoContent() } }

        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                jsonPath("$.options[?(@.title == 'Remove')]") { doesNotExist() }
                jsonPath("$.options[?(@.title == 'Keep')]") { exists() }
            }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    @Test
    fun `POST votes draw - SIMPLE participant draw - returns winner email`() {
        val (token, creatorEmail) = createUser()
        val extra = "draw-part-${UUID.randomUUID()}@test.com"
        val voteId = createSimpleVote(token, participants = listOf(extra))

        val drawResult = parse(draw(token, voteId))

        assertNotNull(
            drawResult["winnerEmail"]?.takeIf { !it.isNull },
            "Expected a winner email"
        )
        assertEquals(1, drawResult["round"].asInt())
        assertEquals(false, drawResult["newRoundStarted"].asBoolean())
    }

    @Test
    fun `POST votes draw - SIMPLE option draw - returns winner option title`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        mockMvc
            .post("/api/v1/votes/$voteId/options") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"Option X"}"""
            }.andExpect { status { isNoContent() } }

        val drawResult = parse(draw(token, voteId))

        assertEquals("Option X", drawResult["winnerOptionTitle"].asText())
        assertTrue(drawResult["winnerEmail"].isNull)
    }

    @Test
    fun `POST votes draw - non-creator - returns 403`() {
        val (creatorToken) = createUser()
        val (otherToken, otherEmail) = createUser()
        val voteId = createSimpleVote(creatorToken, participants = listOf(otherEmail))

        mockMvc
            .post("/api/v1/votes/$voteId/draw") {
                header("Authorization", "Bearer $otherToken")
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `POST votes draw - no participants or options - returns 409`() {
        val (token, creatorEmail) = createUser()
        // Creator is the only participant; remove them
        val voteId = createSimpleVote(token)

        mockMvc
            .delete("/api/v1/votes/$voteId/participants/$creatorEmail") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNoContent() } }

        mockMvc
            .post("/api/v1/votes/$voteId/draw") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `POST votes draw - FAIR_ROTATION ensures all participants win once per round`() {
        // VoteService.createVote always adds creator as participant, so 2 extra = 3 total
        val (token, creatorEmail) = createUser()
        val extraEmails = (1..2).map { "fair-${UUID.randomUUID()}@test.com" }
        val voteId = createFairVote(token, participants = extraEmails)
        val allEmails = setOf(creatorEmail) + extraEmails

        val winnersRound1 = mutableSetOf<String>()

        repeat(3) { iteration ->
            val result = parse(draw(token, voteId))
            val winner = result["winnerEmail"].asText()
            assertTrue(winner in allEmails, "Winner '$winner' must be one of the participants")
            assertTrue(winnersRound1.add(winner), "Participant $winner already won in round 1")
            assertEquals(1, result["round"].asInt())
            assertEquals(false, result["newRoundStarted"].asBoolean())

            if (iteration < 2) reopen(token, voteId)
        }

        assertEquals(3, winnersRound1.size, "All 3 participants must win exactly once in round 1")

        // 4th draw starts a new round
        reopen(token, voteId)
        val round2Result = parse(draw(token, voteId))
        assertEquals(true, round2Result["newRoundStarted"].asBoolean())
        assertEquals(2, round2Result["round"].asInt())
    }

    @Test
    fun `POST votes draw - FAIR_ROTATION options - all options win once per round`() {
        val (token) = createUser()
        val voteId = createFairVote(token, options = listOf("A", "B", "C"))

        val winnersRound1 = mutableSetOf<String>()

        repeat(3) { i ->
            val result = parse(draw(token, voteId))
            val winner = result["winnerOptionTitle"].asText()
            assertTrue(winner in listOf("A", "B", "C"), "Winner must be a known option")
            assertTrue(winnersRound1.add(winner), "Option $winner already won in round 1")
            assertEquals(1, result["round"].asInt())

            if (i < 2) reopen(token, voteId)
        }

        assertEquals(3, winnersRound1.size, "All 3 options must win exactly once in round 1")

        reopen(token, voteId)
        val round2 = parse(draw(token, voteId))
        assertEquals(true, round2["newRoundStarted"].asBoolean())
        assertEquals(2, round2["round"].asInt())
    }

    // ── Reopen ────────────────────────────────────────────────────────────────

    @Test
    fun `POST votes reopen - DRAWN vote - returns 204 and allows another draw`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        draw(token, voteId)

        // Vote is DRAWN; reopen it
        mockMvc
            .post("/api/v1/votes/$voteId/reopen") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNoContent() } }

        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                jsonPath("$.status") { value("PENDING") }
            }

        // Can draw again
        mockMvc
            .post("/api/v1/votes/$voteId/draw") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isOk() } }
    }

    @Test
    fun `POST votes reopen - PENDING vote - returns 409`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        mockMvc
            .post("/api/v1/votes/$voteId/reopen") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isConflict() } }
    }

    // ── Close vote ────────────────────────────────────────────────────────────

    @Test
    fun `POST votes close - PENDING vote - returns 204 and blocks draws`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        mockMvc
            .post("/api/v1/votes/$voteId/close") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNoContent() } }

        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                jsonPath("$.status") { value("CLOSED") }
            }

        // Draw on closed vote must fail
        mockMvc
            .post("/api/v1/votes/$voteId/draw") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `POST votes close - already closed - returns 409`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        mockMvc.post("/api/v1/votes/$voteId/close") { header("Authorization", "Bearer $token") }

        mockMvc
            .post("/api/v1/votes/$voteId/close") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isConflict() } }
    }

    // ── Draw history ──────────────────────────────────────────────────────────

    @Test
    fun `GET votes history - after multiple draws - returns ordered history`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        draw(token, voteId)
        reopen(token, voteId)
        draw(token, voteId)

        val historyResult =
            mockMvc
                .get("/api/v1/votes/$voteId/history") {
                    header("Authorization", "Bearer $token")
                }.andReturn()

        val history = parse(historyResult.response.contentAsString)
        assertTrue(history.isArray, "History must be an array")
        assertEquals(2, history.size(), "Expected 2 history entries")
        // History is ordered desc by drawnAt; first entry is the most recent
        assertNotNull(history[0]["drawnAt"])
        assertNotNull(history[0]["round"])
    }

    @Test
    fun `GET votes history - participant can view - returns 200`() {
        val (creatorToken) = createUser()
        val (participantToken, participantEmail) = createUser()
        val voteId = createSimpleVote(creatorToken, participants = listOf(participantEmail))

        draw(creatorToken, voteId)

        mockMvc
            .get("/api/v1/votes/$voteId/history") {
                header("Authorization", "Bearer $participantToken")
            }.andExpect { status { isOk() } }
    }

    @Test
    fun `GET votes history - empty before any draw`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        val result =
            mockMvc
                .get("/api/v1/votes/$voteId/history") {
                    header("Authorization", "Bearer $token")
                }.andReturn()

        val history = parse(result.response.contentAsString)
        assertTrue(history.isArray)
        assertEquals(0, history.size())
    }

    // ── After-draw state ──────────────────────────────────────────────────────

    @Test
    fun `GET votes by id - after draw - returns lastResult populated`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        draw(token, voteId)

        mockMvc
            .get("/api/v1/votes/$voteId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("DRAWN") }
                jsonPath("$.lastResult") { isMap() }
                jsonPath("$.lastResult.round") { value(1) }
            }
    }

    // ── Modification on non-pending vote ──────────────────────────────────────

    @Test
    fun `POST votes participants - adding participant to DRAWN vote - returns 409`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        draw(token, voteId)

        mockMvc
            .post("/api/v1/votes/$voteId/participants") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"late@test.com"}"""
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `POST votes options - adding option to CLOSED vote - returns 409`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        mockMvc.post("/api/v1/votes/$voteId/close") { header("Authorization", "Bearer $token") }

        mockMvc
            .post("/api/v1/votes/$voteId/options") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"Late Option"}"""
            }.andExpect { status { isConflict() } }
    }
}
