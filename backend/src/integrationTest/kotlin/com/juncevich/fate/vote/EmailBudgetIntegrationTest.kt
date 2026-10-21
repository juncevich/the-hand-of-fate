package com.juncevich.fate.vote

import com.juncevich.fate.AbstractApiIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import java.util.UUID

@TestPropertySource(properties = ["app.notifications.email-budget-per-user=3"])
class EmailBudgetIntegrationTest
    @Autowired
    constructor(
        private val jdbc: JdbcTemplate,
    ) : AbstractApiIntegrationTest() {
        private fun email(prefix: String) = "$prefix-${UUID.randomUUID()}@test.com"

        private fun createVote(
            token: String,
            participants: List<String>,
        ) = mockMvc.post("/api/v1/votes") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content =
                """{"title":"Budget","mode":"SIMPLE","participantEmails":[${participants.joinToString(",") { "\"$it\"" }}]}"""
        }

        private fun outboxJobsFor(recipient: String): Int =
            jdbc.queryForObject(
                "SELECT count(*) FROM notification_outbox WHERE payload::jsonb ->> 'recipientEmail' = ?",
                Int::class.java,
                recipient
            ) ?: 0

        @Test
        fun `invitations beyond the budget are rejected with 429 and leave no trace`() {
            val token = signUp(email("budget-owner"))
            val invited = List(3) { email("invited") }
            val voteId =
                parse(
                    createVote(token, invited.take(2))
                        .andExpect { status { isCreated() } }
                        .andReturn()
                        .response.contentAsString
                ).text("id")

            val extra = email("extra")
            createVote(token, listOf(extra, email("extra2"))).andExpect {
                status { isTooManyRequests() }
                jsonPath("$.title") { value(org.hamcrest.Matchers.startsWith("Email limit reached")) }
            }
            assertEquals(0, outboxJobsFor(extra))

            // Deleting the vote must not free budget.
            mockMvc
                .delete("/api/v1/votes/$voteId") { header("Authorization", "Bearer $token") }
                .andExpect { status { isNoContent() } }
            createVote(token, listOf(invited[2])).andExpect { status { isCreated() } }
            createVote(token, listOf(email("one-too-many"))).andExpect { status { isTooManyRequests() } }
        }

        @Test
        fun `draws over the budget succeed without result emails`() {
            val token = signUp(email("drawer"))
            val invited = email("invited")
            val voteId =
                parse(
                    createVote(token, listOf(invited))
                        .andExpect { status { isCreated() } }
                        .andReturn()
                        .response.contentAsString
                ).text("id")

            // 1 invitation + 2 result emails (creator and participant) = budget of 3.
            mockMvc
                .post("/api/v1/votes/$voteId/draw") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
            assertEquals(2, outboxJobsFor(invited))

            mockMvc.post("/api/v1/votes/$voteId/reopen") { header("Authorization", "Bearer $token") }
            mockMvc
                .post("/api/v1/votes/$voteId/draw") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
            assertEquals(2, outboxJobsFor(invited))
        }
    }
