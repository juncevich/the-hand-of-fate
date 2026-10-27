package com.juncevich.fate.vote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/** Options of a vote: draw target rules, adding/removing/reordering, and drawing from options. */
class VoteOptionsApiIntegrationTest : AbstractVoteApiIntegrationTest() {
    @Test
    fun `POST votes options - participants vote - returns 409`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token)

        mockMvc
            .post("/api/v1/votes/$voteId/options") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"Option Alpha"}"""
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `GET votes by id - exposes the draw target`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token, drawTarget = "OPTIONS")

        mockMvc
            .get("/api/v1/votes/$voteId") { header("Authorization", "Bearer $token") }
            .andExpect { jsonPath("$.drawTarget") { value("OPTIONS") } }
    }

    @Test
    fun `POST votes options - add option - returns 204`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token, drawTarget = "OPTIONS")

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
    fun `PUT votes options order - reorders, swapping positions under the unique constraint`() {
        val (token) = createUser()
        val voteId = createFairVote(token, options = listOf("A", "B", "C"))

        fun options(field: String): List<String> =
            parse(
                mockMvc
                    .get("/api/v1/votes/$voteId") { header("Authorization", "Bearer $token") }
                    .andReturn()
                    .response.contentAsString
            )["options"]
                .toList()
                .map { it[field].asString() }

        val (a, b, c) = options("id")
        mockMvc
            .put("/api/v1/votes/$voteId/options/order") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"optionIds":["$c","$a","$b"]}"""
            }.andExpect { status { isNoContent() } }
        assertEquals(listOf("C", "A", "B"), options("title"))

        // A new option goes last, after the reordered ones.
        mockMvc.post("/api/v1/votes/$voteId/options") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"D"}"""
        }
        assertEquals(listOf("C", "A", "B", "D"), options("title"))

        mockMvc
            .put("/api/v1/votes/$voteId/options/order") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"optionIds":["$a"]}"""
            }.andExpect { status { isBadRequest() } }
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
        val optionToRemove = checkNotNull(options.find { it["title"].asString() == "Remove" })
        val optionId = optionToRemove["id"].asString()

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
    fun `POST votes draw - SIMPLE option draw - returns winner option title`() {
        val (token) = createUser()
        val voteId = createSimpleVote(token, drawTarget = "OPTIONS")

        mockMvc
            .post("/api/v1/votes/$voteId/options") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"Option X"}"""
            }.andExpect { status { isNoContent() } }

        val drawResult = parse(draw(token, voteId))

        assertEquals("Option X", drawResult["winnerOptionTitle"].asString())
        assertTrue(drawResult["winnerEmail"].isNull)
    }

    @Test
    fun `POST votes draw - FAIR_ROTATION options - all options win once per round`() {
        val (token) = createUser()
        val voteId = createFairVote(token, options = listOf("A", "B", "C"))

        val winnersRound1 = mutableSetOf<String>()

        repeat(3) { i ->
            val result = parse(draw(token, voteId))
            val winner = result["winnerOptionTitle"].asString()
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
