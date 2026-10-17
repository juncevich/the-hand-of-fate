package com.juncevich.fate.auth

import com.juncevich.fate.AbstractApiIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/** Deleting an account end to end: what goes, what stays anonymized, what other users still see. */
class AccountDeletionIntegrationTest : AbstractApiIntegrationTest() {
    private fun email(prefix: String) = "$prefix-${UUID.randomUUID()}@test.com"

    private fun createVote(
        token: String,
        participants: List<String>,
    ): String =
        parse(
            mockMvc
                .post("/api/v1/votes") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"title":"V","mode":"SIMPLE","participantEmails":[${participants.joinToString(",") { "\"$it\"" }}]}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        ).text("id")

    private fun count(
        sql: String,
        vararg args: Any,
    ) = jdbcTemplate.queryForObject(sql, Int::class.java, *args) ?: 0

    @Test
    fun `deleting an account removes its votes and data and anonymizes it in other votes`() {
        val annEmail = email("ann")
        val bobEmail = email("bob")
        val ann = registerAndGetToken(annEmail)
        val bob = registerAndGetToken(bobEmail)

        val annsVote = createVote(ann, listOf(bobEmail))
        // Bob's vote where Ann is the only participant left, so she wins its draw.
        val bobsVote = createVote(bob, listOf(annEmail))
        mockMvc.delete("/api/v1/votes/$bobsVote/participants/$bobEmail") { header("Authorization", "Bearer $bob") }
        mockMvc
            .post("/api/v1/votes/$bobsVote/draw") { header("Authorization", "Bearer $bob") }
            .andExpect { jsonPath("$.winnerEmail") { value(annEmail) } }
        assertTrue(count("SELECT count(*) FROM notification_outbox WHERE payload::jsonb ->> 'recipientEmail' = ?", annEmail) > 0)

        mockMvc
            .post("/api/v1/auth/delete-account") {
                header("Authorization", "Bearer $ann")
                contentType = MediaType.APPLICATION_JSON
                content = """{"password":"wrong-password"}"""
            }.andExpect { jsonPath("$.code") { value("WRONG_PASSWORD") } }
        mockMvc
            .post("/api/v1/auth/delete-account") {
                header("Authorization", "Bearer $ann")
                contentType = MediaType.APPLICATION_JSON
                content = """{"password":"password123"}"""
            }.andExpect { status { isNoContent() } }

        assertEquals(0, count("SELECT count(*) FROM users WHERE email = ?", annEmail))
        assertEquals(0, count("SELECT count(*) FROM votes WHERE id = ?::uuid", annsVote))
        assertEquals(0, count("SELECT count(*) FROM vote_participants WHERE email = ?", annEmail))
        assertEquals(0, count("SELECT count(*) FROM draw_history WHERE winner_email = ?", annEmail))
        assertEquals(0, count("SELECT count(*) FROM notification_outbox WHERE payload::jsonb ->> 'recipientEmail' = ?", annEmail))

        // Bob still sees his vote and its history, with the winner anonymized.
        mockMvc.get("/api/v1/votes/$bobsVote/history/page") { header("Authorization", "Bearer $bob") }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].winnerDisplayName") { value("Deleted user") }
            jsonPath("$.content[0].winnerEmail") { value(null as Any?) }
        }
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$annEmail","password":"password123"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `the stats tell how many votes deleting the account would delete`() {
        val token = registerAndGetToken(email("stats"))
        createVote(token, emptyList())
        createVote(token, emptyList())

        mockMvc
            .get("/api/v1/votes/stats") { header("Authorization", "Bearer $token") }
            .andExpect { jsonPath("$.createdCount") { value(2) } }
    }
}
