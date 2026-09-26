package com.juncevich.fate.vote.internal.notification

import com.juncevich.fate.auth.User
import com.juncevich.fate.vote.DrawResult
import com.juncevich.fate.vote.internal.ParticipantInvited
import com.juncevich.fate.vote.internal.VoteDrawn
import com.juncevich.fate.vote.internal.domain.Vote
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    fun `on ParticipantInvited - succeeds on first attempt without incrementing failure counter`() {
        every { emailService.sendVoteInvitation(any(), any(), any(), any()) } returns Unit

        notificationAdapter.on(ParticipantInvited(vote.id, vote.title, creator.displayName, "participant@test.com"))

        verify(exactly = 1) { emailService.sendVoteInvitation(any(), any(), any(), any()) }
        verify(exactly = 0) { meterRegistry.counter("notification.failed", "type", any()) }
    }

    @Test
    fun `on VoteDrawn - increments failure counter tagged by type when sending fails`() {
        every { emailService.sendDrawResult(any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("SMTP down")
        val result = DrawResult("winner@test.com", "Winner", null, 1, false)

        notificationAdapter.on(VoteDrawn(vote.id, vote.title, result, listOf("winner@test.com")))

        // Retries happen inside EmailService (@Retryable, see NotificationRetryTest)
        verify(exactly = 1) { emailService.sendDrawResult(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { meterRegistry.counter("notification.failed", "type", "draw-result") }
        verify(exactly = 1) { counter.increment() }
    }

    @Test
    fun `on VoteDrawn - sends one email per participant`() {
        every { emailService.sendDrawResult(any(), any(), any(), any(), any(), any()) } returns Unit
        val result = DrawResult("winner@test.com", "Winner", null, 1, false)

        notificationAdapter.on(VoteDrawn(vote.id, vote.title, result, listOf("a@test.com", "b@test.com")))

        verify(exactly = 1) { emailService.sendDrawResult("a@test.com", any(), any(), any(), any(), any()) }
        verify(exactly = 1) { emailService.sendDrawResult("b@test.com", any(), any(), any(), any(), any()) }
    }

    @Test
    fun `on VoteDrawn - sends to all participants concurrently`() {
        val recipients = listOf("a@test.com", "b@test.com", "c@test.com")
        // Each send waits until every send has started, which only completes if they run in parallel
        val allStarted = CountDownLatch(recipients.size)
        val sawEveryoneInFlight = mutableListOf<Boolean>()
        every { emailService.sendDrawResult(any(), any(), any(), any(), any(), any()) } answers {
            allStarted.countDown()
            val ok = allStarted.await(5, TimeUnit.SECONDS)
            synchronized(sawEveryoneInFlight) { sawEveryoneInFlight += ok }
        }
        val result = DrawResult(null, null, "Option", 1, false)

        notificationAdapter.on(VoteDrawn(vote.id, vote.title, result, recipients))

        assertTrue(sawEveryoneInFlight.size == recipients.size && sawEveryoneInFlight.all { it })
    }
}
