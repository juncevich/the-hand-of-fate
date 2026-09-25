package com.juncevich.fate.vote

import com.juncevich.fate.AbstractApiIntegrationTest
import com.juncevich.fate.vote.internal.notification.EmailService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * End-to-end check of when vote notifications are dispatched: only after the surrounding
 * transaction commits, asynchronously, and never for a rolled-back change.
 */
class NotificationDeliveryIntegrationTest : AbstractApiIntegrationTest() {
    @MockkBean(relaxed = true)
    private lateinit var emailService: EmailService

    @Autowired
    private lateinit var voteService: VoteService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun resetMock() {
        clearMocks(emailService)
        every { emailService.sendVoteInvitation(any(), any(), any(), any()) } returns Unit
        every { emailService.sendDrawResult(any(), any(), any(), any(), any(), any()) } returns Unit
    }

    private fun register(email: String): Pair<String, UUID> {
        val body =
            parse(
                mockMvc
                    .post("/api/v1/auth/register") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"email":"$email","password":"password123","displayName":"Creator"}"""
                    }.andReturn()
                    .response.contentAsString
            )
        return body.text("accessToken") to UUID.fromString(body.text("userId"))
    }

    private fun createVote(
        token: String,
        participants: List<String>,
        options: List<String> = emptyList(),
    ): String {
        val emails = participants.joinToString(",") { "\"$it\"" }
        val opts = options.joinToString(",") { "\"$it\"" }
        val result =
            mockMvc
                .post("/api/v1/votes") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"title":"Notify","mode":"SIMPLE","participantEmails":[$emails],"options":[$opts]}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
        return parse(result.response.contentAsString).text("id")
    }

    @Test
    fun `creating a vote sends one invitation per invited participant, not to the creator`() {
        val creatorEmail = "creator-${UUID.randomUUID()}@test.com"
        val (token, _) = register(creatorEmail)
        val a = "a-${UUID.randomUUID()}@test.com"
        val b = "b-${UUID.randomUUID()}@test.com"

        createVote(token, listOf(a, b))

        verify(timeout = 5_000, exactly = 1) { emailService.sendVoteInvitation(a, "Notify", "Creator", any()) }
        verify(timeout = 5_000, exactly = 1) { emailService.sendVoteInvitation(b, "Notify", "Creator", any()) }
        verify(exactly = 0) { emailService.sendVoteInvitation(creatorEmail, any(), any(), any()) }
    }

    @Test
    fun `drawing sends the result to every participant including the creator`() {
        val creatorEmail = "creator-${UUID.randomUUID()}@test.com"
        val (token, _) = register(creatorEmail)
        val a = "a-${UUID.randomUUID()}@test.com"
        val voteId = createVote(token, listOf(a), options = listOf("Only option"))

        mockMvc
            .post("/api/v1/votes/$voteId/draw") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }

        for (recipient in listOf(creatorEmail, a)) {
            verify(timeout = 5_000, exactly = 1) {
                emailService.sendDrawResult(recipient, "Notify", "Only option", any(), 1, any())
            }
        }
    }

    @Test
    fun `no invitation is sent when the transaction adding the participant rolls back`() {
        val (token, creatorId) = register("creator-${UUID.randomUUID()}@test.com")
        val voteId = UUID.fromString(createVote(token, emptyList()))
        val rolledBack = "rolled-back-${UUID.randomUUID()}@test.com"
        val committed = "committed-${UUID.randomUUID()}@test.com"

        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            voteService.addParticipant(voteId, creatorId, rolledBack)
            status.setRollbackOnly()
        }
        // A committed change afterwards acts as a barrier: once its email arrives, any
        // (wrongly) dispatched email for the rolled-back change would have arrived too.
        voteService.addParticipant(voteId, creatorId, committed)

        verify(timeout = 5_000, exactly = 1) { emailService.sendVoteInvitation(committed, any(), any(), any()) }
        verify(exactly = 0) { emailService.sendVoteInvitation(rolledBack, any(), any(), any()) }
    }
}
