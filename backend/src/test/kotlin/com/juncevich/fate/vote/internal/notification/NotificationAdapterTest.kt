package com.juncevich.fate.vote.internal.notification

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class NotificationAdapterTest {
    private val emailService = mockk<EmailService>()
    private val adapter = NotificationAdapter(emailService, "http://localhost:3000")
    private val voteId = UUID.randomUUID()

    @Test
    fun `send invitation - renders the vote link for the recipient`() {
        every { emailService.sendVoteInvitation(any(), any(), any(), any()) } returns Unit

        adapter.send(InvitationPayload(voteId, "Vote", "Creator", "p@test.com"))

        verify(exactly = 1) {
            emailService.sendVoteInvitation("p@test.com", "Vote", "Creator", "http://localhost:3000/votes/$voteId")
        }
    }

    @Test
    fun `send draw result - passes the winner and blanks a missing winner email`() {
        every { emailService.sendDrawResult(any(), any(), any(), any(), any(), any()) } returns Unit

        adapter.send(DrawResultPayload(voteId, "Vote", "p@test.com", "Option A", null, 2))

        verify(exactly = 1) {
            emailService.sendDrawResult("p@test.com", "Vote", "Option A", "", 2, "http://localhost:3000/votes/$voteId")
        }
    }

    @Test
    fun `send - propagates failures so the worker can retry`() {
        every { emailService.sendDrawResult(any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("SMTP down")

        assertThrows<RuntimeException> {
            adapter.send(DrawResultPayload(voteId, "Vote", "p@test.com", "Winner", "w@test.com", 1))
        }
    }
}
