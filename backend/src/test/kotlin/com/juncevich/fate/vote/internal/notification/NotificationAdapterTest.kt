package com.juncevich.fate.vote.internal.notification

import com.juncevich.fate.auth.User
import com.juncevich.fate.vote.DrawResult
import com.juncevich.fate.vote.internal.domain.Vote
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationAdapterTest {
    private val emailService = mockk<EmailService>()
    private val meterRegistry = mockk<MeterRegistry>()
    private val counter = mockk<Counter>(relaxed = true)

    private val notificationAdapter = NotificationAdapter(emailService, meterRegistry, "http://localhost:3000")

    private val creator = User(email = "creator@test.com", passwordHash = "hash", displayName = "Creator")
    private val vote = Vote(title = "Test Vote", creator = creator)

    @BeforeEach
    fun setUp() {
        every { meterRegistry.counter("notification.failed", "type", any()) } returns counter
    }

    @Test
    fun `notifyVoteInvitation - succeeds on first attempt without incrementing failure counter`() {
        every { emailService.sendVoteInvitation(any(), any(), any(), any()) } returns Unit

        notificationAdapter.notifyVoteInvitation("participant@test.com", vote)

        verify(exactly = 1) { emailService.sendVoteInvitation(any(), any(), any(), any()) }
        verify(exactly = 0) { meterRegistry.counter("notification.failed", "type", any()) }
    }

    @Test
    fun `notifyVoteInvitation - retries and eventually succeeds`() {
        every { emailService.sendVoteInvitation(any(), any(), any(), any()) } throws
            RuntimeException("SMTP down") andThenThrows
            RuntimeException("SMTP still down") andThen Unit

        notificationAdapter.notifyVoteInvitation("participant@test.com", vote)

        verify(exactly = 3) { emailService.sendVoteInvitation(any(), any(), any(), any()) }
        verify(exactly = 0) { meterRegistry.counter("notification.failed", "type", any()) }
    }

    @Test
    fun `notifyDrawResult - increments failure counter tagged by type when all attempts fail`() {
        every { emailService.sendDrawResult(any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("SMTP down")
        val result = DrawResult("winner@test.com", "Winner", null, 1, false)

        notificationAdapter.notifyDrawResult(vote, result, listOf("winner@test.com"))

        verify(exactly = 3) { emailService.sendDrawResult(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { meterRegistry.counter("notification.failed", "type", "draw-result") }
        verify(exactly = 1) { counter.increment() }
    }

    @Test
    fun `notifyDrawResult - sends one email per participant`() {
        every { emailService.sendDrawResult(any(), any(), any(), any(), any(), any()) } returns Unit
        val result = DrawResult("winner@test.com", "Winner", null, 1, false)

        notificationAdapter.notifyDrawResult(vote, result, listOf("a@test.com", "b@test.com"))

        verify(exactly = 1) { emailService.sendDrawResult("a@test.com", any(), any(), any(), any(), any()) }
        verify(exactly = 1) { emailService.sendDrawResult("b@test.com", any(), any(), any(), any(), any()) }
    }
}
