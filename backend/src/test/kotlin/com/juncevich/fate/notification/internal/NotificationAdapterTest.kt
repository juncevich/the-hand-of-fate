package com.juncevich.fate.notification.internal

import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.EmailPreferences
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.NOTIFICATION_PAYLOAD_VERSION
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class NotificationAdapterTest {
    private val emailService = mockk<EmailService>()
    private val preferences = EmailPreferences(mockk(), "test-unsubscribe-secret")
    private val adapter = NotificationAdapter(emailService, preferences, "http://localhost:3000")
    private val voteId = UUID.randomUUID()

    @Test
    fun `send invitation - renders the vote link for the recipient`() {
        every { emailService.sendVoteInvitation(any(), any(), any(), any(), any()) } returns Unit

        adapter.send(InvitationPayload(voteId, "Vote", "Creator", "p@test.com"))

        verify(exactly = 1) {
            emailService.sendVoteInvitation("p@test.com", "Vote", "Creator", "http://localhost:3000/votes/$voteId", any())
        }
    }

    @Test
    fun `notification emails carry unsubscribe links signed for their recipient`() {
        val links = slot<UnsubscribeLinks>()
        every { emailService.sendVoteInvitation(any(), any(), any(), any(), capture(links)) } returns Unit

        adapter.send(InvitationPayload(voteId, "Vote", "Creator", "p+1@test.com"))

        val sig = preferences.sign("p+1@test.com")
        assertEquals("http://localhost:3000/unsubscribe?email=p%2B1%40test.com&sig=$sig", links.captured.page)
        assertEquals(
            "http://localhost:3000/api/v1/notifications/unsubscribe/one-click?email=p%2B1%40test.com&sig=$sig",
            links.captured.oneClick
        )
    }

    @Test
    fun `send draw result - passes the winner and blanks a missing winner email`() {
        every { emailService.sendDrawResult(any(), any(), any(), any(), any(), any(), any()) } returns Unit

        adapter.send(DrawResultPayload(voteId, "Vote", "p@test.com", "Option A", null, 2))

        verify(exactly = 1) {
            emailService.sendDrawResult("p@test.com", "Vote", "Option A", "", 2, "http://localhost:3000/votes/$voteId", any())
        }
    }

    @Test
    fun `send - propagates failures so the worker can retry`() {
        every { emailService.sendDrawResult(any(), any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("SMTP down")

        assertThrows<RuntimeException> {
            adapter.send(DrawResultPayload(voteId, "Vote", "p@test.com", "Winner", "w@test.com", 1))
        }
    }
}
