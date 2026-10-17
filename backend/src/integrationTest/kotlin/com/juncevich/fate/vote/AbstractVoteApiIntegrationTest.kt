package com.juncevich.fate.vote

import com.juncevich.fate.AbstractApiIntegrationTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * Vote-API helpers shared by the vote integration test classes. Abstract so it is never run on
 * its own, even though it declares no abstract members.
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AbstractVoteApiIntegrationTest : AbstractApiIntegrationTest() {
    protected fun createUser(): Pair<String, String> {
        val email = "vote-user-${UUID.randomUUID()}@test.com"
        val token = registerAndGetToken(email)
        return token to email
    }

    protected fun createSimpleVote(
        token: String,
        title: String = "Test Vote",
        participants: List<String> = emptyList(),
        drawTarget: String = "PARTICIPANTS",
    ): String {
        val participantsJson = participants.joinToString(",") { "\"$it\"" }
        val result =
            mockMvc
                .post("/api/v1/votes") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"title":"$title","mode":"SIMPLE","participantEmails":[$participantsJson],""" +
                        """"drawTarget":"$drawTarget"}"""
                }.andReturn()
        return parse(result.response.contentAsString)["id"].asString()
    }

    protected fun createFairVote(
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
        return parse(result.response.contentAsString)["id"].asString()
    }

    protected fun draw(
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

    protected fun reopen(
        token: String,
        voteId: String,
    ) {
        mockMvc
            .post("/api/v1/votes/$voteId/reopen") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNoContent() } }
    }
}
