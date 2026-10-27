package com.juncevich.fate.vote.notification

import com.juncevich.fate.NOW
import com.juncevich.fate.account.UserProfile
import com.juncevich.fate.account.UserQueryService
import com.juncevich.fate.notification.DrawResultPayload
import com.juncevich.fate.notification.EmailBudget
import com.juncevich.fate.notification.InvitationPayload
import com.juncevich.fate.notification.NotificationOutbox
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.TooManyRequestsException
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.vote.VoteId
import com.juncevich.fate.vote.domain.DrawHistory
import com.juncevich.fate.vote.domain.DrawOutcome
import com.juncevich.fate.vote.domain.ParticipantInvited
import com.juncevich.fate.vote.domain.VoteCreated
import com.juncevich.fate.vote.domain.VoteDrawn
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class NotificationRecorderTest {
    private val outbox = mockk<NotificationOutbox>(relaxed = true)
    private val budget = mockk<EmailBudget>(relaxed = true)
    private val users = mockk<UserQueryService>()
    private val recorder = NotificationRecorder(outbox, budget, users)

    private val creator = UserProfile(UserId(UUID.randomUUID()), Email.of("creator@test.com"), "Creator")
    private val creatorId = creator.id
    private val voteId = VoteId(UUID.randomUUID())

    init {
        every { users.findProfileById(creator.id) } returns creator
    }

    @Test
    fun `a new vote charges the creator once and invites every invitee by the creator's name`() {
        recorder.on(VoteCreated(voteId, "Vote", creatorId, listOf(Email.of("a@test.com"), Email.of("b@test.com"))))

        verify(exactly = 1) { budget.reserveOrThrow(creator.id, 2) }
        listOf("a@test.com", "b@test.com").forEach { email ->
            verify { outbox.record(InvitationPayload(voteId.value, "Vote", "Creator", email)) }
        }
    }

    @Test
    fun `a vote without invitees sends and charges nothing`() {
        recorder.on(VoteCreated(voteId, "Vote", creatorId, emptyList()))

        verify(exactly = 0) { budget.reserveOrThrow(any(), any()) }
        verify(exactly = 0) { outbox.record(any()) }
    }

    @Test
    fun `an invitation over the budget is refused before anything is recorded`() {
        every { budget.reserveOrThrow(creator.id, 1) } throws TooManyRequestsException("Email limit reached", ErrorCode.EMAIL_LIMIT_REACHED)

        assertThrows<TooManyRequestsException> {
            recorder.on(ParticipantInvited(voteId, "Vote", creatorId, Email.of("new@test.com")))
        }

        verify(exactly = 0) { outbox.record(any()) }
    }

    @Test
    fun `an added participant is invited by the creator's name`() {
        recorder.on(ParticipantInvited(voteId, "Vote", creatorId, Email.of("new@test.com")))

        verify { outbox.record(InvitationPayload(voteId.value, "Vote", "Creator", "new@test.com")) }
    }

    @Test
    fun `a draw tells every distinct recipient who won`() {
        every { budget.tryReserve(creator.id, 2) } returns true

        recorder.on(drawn(listOf("a@test.com", "b@test.com", "a@test.com")))

        listOf("a@test.com", "b@test.com").forEach { email ->
            verify(exactly = 1) {
                outbox.record(DrawResultPayload(voteId.value, "Vote", email, "Winner", "w@test.com", 2))
            }
        }
    }

    @Test
    fun `a draw over the budget sends no result emails`() {
        every { budget.tryReserve(creator.id, 1) } returns false

        recorder.on(drawn(listOf("a@test.com")))

        verify(exactly = 0) { outbox.record(any()) }
    }

    private fun drawn(recipients: List<String>): VoteDrawn {
        val entry =
            DrawHistory.ParticipantWinner(
                voteId = voteId,
                email = Email.of("w@test.com"),
                displayName = "Winner",
                round = 2,
                drawnAt = NOW
            )
        return VoteDrawn(voteId, "Vote", creatorId, DrawOutcome(entry, newRoundStarted = false), recipients.map(Email::of))
    }
}
