package com.juncevich.fate.vote.domain

import com.juncevich.fate.NOW
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.FateException
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.vote.DrawTarget
import com.juncevich.fate.vote.VoteId
import com.juncevich.fate.vote.VoteLimits
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

private val CREATOR_ID = UserId(UUID.randomUUID())
private const val CREATOR_EMAIL = "creator@test.com"

private fun vote(
    mode: VoteMode = VoteMode.SIMPLE,
    drawTarget: DrawTarget = DrawTarget.PARTICIPANTS,
    status: VoteStatus = VoteStatus.PENDING,
    currentRound: Int = 1,
    participants: List<String> = listOf(CREATOR_EMAIL),
    options: List<String> = emptyList(),
) = Vote(
    id = VoteId(UUID.randomUUID()),
    title = "Vote",
    description = null,
    creatorId = CREATOR_ID,
    mode = mode,
    drawTarget = drawTarget,
    status = status,
    currentRound = currentRound,
    createdAt = NOW,
    version = 0,
    participants = participants.map { Participant(Email.of(it), addedAt = NOW) },
    options = options.mapIndexed { position, title -> VoteOption(title, position, createdAt = NOW) }
)

private fun create(
    participantEmails: List<String> = emptyList(),
    optionTitles: List<String> = emptyList(),
    drawTarget: DrawTarget? = null,
    title: String = "Vote",
    description: String? = null,
    displayNames: Map<String, String> = emptyMap(),
) = Vote.create(
    creatorId = CREATOR_ID,
    creatorEmail = Email.of(CREATOR_EMAIL),
    title = title,
    description = description,
    mode = VoteMode.SIMPLE,
    drawTarget = drawTarget,
    participantEmails = participantEmails,
    optionTitles = optionTitles,
    now = NOW
) { emails -> displayNames.mapKeys { Email.of(it.key) }.filterKeys { it in emails } }

private val noHistory: (Int) -> List<DrawHistory> = { emptyList() }

private inline fun <reified T : FateException> assertFails(
    code: ErrorCode,
    block: () -> Unit,
) {
    assertEquals(code, assertThrows<T>(block).code)
}

class VoteTest {
    @Nested
    inner class Create {
        @Test
        fun `the creator joins the invited participants, normalized and without duplicates`() {
            val vote = create(listOf(" P@Test.com ", "p@test.com", CREATOR_EMAIL.uppercase()), displayNames = mapOf("p@test.com" to "Pat"))

            assertEquals(listOf(CREATOR_EMAIL, "p@test.com"), vote.participants.map { it.email.value })
            assertEquals("Pat", vote.participants.single { it.email.value == "p@test.com" }.displayName)
            assertEquals(VoteStatus.PENDING, vote.status)
            assertEquals(1, vote.currentRound)
            assertEquals(0, vote.version)
        }

        @Test
        fun `options are trimmed, deduplicated and numbered in order`() {
            val vote = create(optionTitles = listOf(" A ", "B", "A"))

            assertEquals(listOf("A" to 0, "B" to 1), vote.options.map { it.title to it.position })
        }

        @Test
        fun `the draw target is inferred from the options when none is given`() {
            assertEquals(DrawTarget.OPTIONS, create(optionTitles = listOf("A")).drawTarget)
            assertEquals(DrawTarget.PARTICIPANTS, create().drawTarget)
            assertEquals(DrawTarget.OPTIONS, create(drawTarget = DrawTarget.OPTIONS).drawTarget)
        }

        @Test
        fun `a vote drawing from participants cannot have options`() {
            assertFails<BadRequestException>(ErrorCode.OPTIONS_NOT_ALLOWED) {
                create(optionTitles = listOf("A"), drawTarget = DrawTarget.PARTICIPANTS)
            }
        }

        @Test
        fun `the title and description are validated`() {
            assertFails<BadRequestException>(ErrorCode.TITLE_INVALID) { create(title = " ") }
            assertFails<BadRequestException>(ErrorCode.DESCRIPTION_TOO_LONG) {
                create(description = "x".repeat(VoteLimits.MAX_DESCRIPTION_LENGTH + 1))
            }
        }

        @Test
        fun `a malformed participant email is rejected`() {
            assertThrows<BadRequestException> { create(listOf("not-an-email")) }
        }

        @Test
        fun `the participant limit counts the creator`() {
            assertFails<BadRequestException>(ErrorCode.TOO_MANY_PARTICIPANTS) {
                create(List(VoteLimits.MAX_PARTICIPANTS) { "p$it@test.com" })
            }
        }

        @Test
        fun `an oversized participant list is rejected before normalizing it`() {
            val ex = assertThrows<BadRequestException> { create(List(VoteLimits.MAX_PARTICIPANTS + 1) { "not-an-email" }) }
            assertEquals("A vote can have at most ${VoteLimits.MAX_PARTICIPANTS} participants", ex.message)
        }

        @Test
        fun `more options than the limit are rejected`() {
            assertFails<BadRequestException>(ErrorCode.TOO_MANY_OPTIONS) {
                create(optionTitles = List(VoteLimits.MAX_OPTIONS + 1) { "Option $it" })
            }
        }
    }

    @Nested
    inner class Access {
        @Test
        fun `the creator and participants may view, others may not`() {
            val vote = vote(participants = listOf(CREATOR_EMAIL, "p@test.com"))

            vote.requireViewer(CREATOR_ID, Email.of("whatever@test.com"))
            vote.requireViewer(UserId(UUID.randomUUID()), Email.of("p@test.com"))
            assertFails<ForbiddenException>(ErrorCode.VOTE_ACCESS_DENIED) { vote.requireViewer(UserId(UUID.randomUUID()), Email.of("x@test.com")) }
        }

        @Test
        fun `only the creator may change the vote`() {
            val stranger = UserId(UUID.randomUUID())
            val vote = vote(drawTarget = DrawTarget.OPTIONS, options = listOf("A"))
            val changes: List<() -> Unit> =
                listOf(
                    { vote.requireCreator(stranger) },
                    { vote.invite(stranger, "new@test.com", NOW) { null } },
                    { vote.removeParticipant(stranger, CREATOR_EMAIL) },
                    { vote.addOption(stranger, "B", NOW) },
                    { vote.reorderOptions(stranger, vote.options.map { it.id }) },
                    { vote.removeOption(stranger, vote.options.single().id) },
                    { vote.draw(stranger, WinnerPicker(), NOW, noHistory) },
                    { vote.reopen(stranger) },
                    { vote.close(stranger) }
                )
            changes.forEach { assertFails<ForbiddenException>(ErrorCode.NOT_VOTE_CREATOR, it) }
            assertEquals(VoteStatus.PENDING, vote.status)
        }

        @Test
        fun `participants and options change only while the vote is pending`() {
            val vote = vote(drawTarget = DrawTarget.OPTIONS, status = VoteStatus.DRAWN, options = listOf("A"))
            val changes: List<() -> Unit> =
                listOf(
                    { vote.invite(CREATOR_ID, "new@test.com", NOW) { null } },
                    { vote.removeParticipant(CREATOR_ID, CREATOR_EMAIL) },
                    { vote.addOption(CREATOR_ID, "B", NOW) },
                    { vote.reorderOptions(CREATOR_ID, vote.options.map { it.id }) },
                    { vote.removeOption(CREATOR_ID, vote.options.single().id) }
                )
            changes.forEach { assertFails<ConflictException>(ErrorCode.VOTE_NOT_PENDING, it) }
            assertEquals(1, vote.participants.size)
            assertEquals(1, vote.options.size)
        }
    }

    @Nested
    inner class Participants {
        @Test
        fun `an invited participant is normalized and named after their account`() {
            val vote = vote()

            val participant = vote.invite(CREATOR_ID, " New@Test.com ", NOW) { if (it == Email.of("new@test.com")) "Newton" else null }

            assertEquals("new@test.com", participant.email.value)
            assertEquals("Newton", participant.displayName)
            assertEquals(listOf(CREATOR_EMAIL, "new@test.com"), vote.participants.map { it.email.value })
        }

        @Test
        fun `a participant cannot be invited twice`() {
            val vote = vote(participants = listOf(CREATOR_EMAIL, "dup@test.com"))
            assertFails<ConflictException>(ErrorCode.PARTICIPANT_EXISTS) { vote.invite(CREATOR_ID, "DUP@test.com", NOW) { null } }
        }

        @Test
        fun `a full vote takes no more participants`() {
            val vote = vote(participants = List(VoteLimits.MAX_PARTICIPANTS) { "p$it@test.com" })
            assertFails<BadRequestException>(ErrorCode.TOO_MANY_PARTICIPANTS) { vote.invite(CREATOR_ID, "new@test.com", NOW) { null } }
        }

        @Test
        fun `removing a participant matches the normalized address and ignores unknown ones`() {
            val vote = vote(participants = listOf(CREATOR_EMAIL, "p@test.com"))

            vote.removeParticipant(CREATOR_ID, " P@test.com")
            vote.removeParticipant(CREATOR_ID, "unknown@test.com")

            assertEquals(listOf(CREATOR_EMAIL), vote.participants.map { it.email.value })
        }

        @Test
        fun `the creator may leave the draw and still runs and sees the vote`() {
            val vote = vote(participants = listOf(CREATOR_EMAIL, "p@test.com"))

            vote.removeParticipant(CREATOR_ID, CREATOR_EMAIL)

            assertEquals(listOf("p@test.com"), vote.participants.map { it.email.value })
            vote.requireViewer(CREATOR_ID, Email.of(CREATOR_EMAIL))
            val entry = vote.draw(CREATOR_ID, WinnerPicker(), NOW, noHistory).entry as DrawHistory.ParticipantWinner
            assertEquals("p@test.com", entry.email?.value)
        }
    }

    @Nested
    inner class Options {
        @Test
        fun `an added option is trimmed and appended after the highest position, even past a gap`() {
            val vote = vote(drawTarget = DrawTarget.OPTIONS, options = listOf("A", "B", "C"))
            vote.removeOption(CREATOR_ID, vote.options[1].id)

            val option = vote.addOption(CREATOR_ID, "  D  ", NOW)

            assertEquals("D", option.title)
            assertEquals(3, option.position)
            assertEquals(listOf("A", "C", "D"), vote.options.map { it.title })
        }

        @Test
        fun `options are kept in position order`() {
            val options = listOf(VoteOption("Second", 1, createdAt = NOW), VoteOption("First", 0, createdAt = NOW))
            val vote = Vote(VoteId(UUID.randomUUID()), "Vote", null, CREATOR_ID, VoteMode.SIMPLE, DrawTarget.OPTIONS, VoteStatus.PENDING, 1, NOW, 0, emptyList(), options)

            assertEquals(listOf("First", "Second"), vote.options.map { it.title })
        }

        @Test
        fun `an option title is validated and unique within the vote`() {
            val vote = vote(drawTarget = DrawTarget.OPTIONS, options = listOf("Winner"))

            assertFails<BadRequestException>(ErrorCode.TITLE_INVALID) { vote.addOption(CREATOR_ID, " ", NOW) }
            assertFails<ConflictException>(ErrorCode.ALREADY_EXISTS) { vote.addOption(CREATOR_ID, " Winner ", NOW) }
        }

        @Test
        fun `a full vote takes no more options`() {
            val vote = vote(drawTarget = DrawTarget.OPTIONS, options = List(VoteLimits.MAX_OPTIONS) { "Option $it" })
            assertFails<BadRequestException>(ErrorCode.TOO_MANY_OPTIONS) { vote.addOption(CREATOR_ID, "One more", NOW) }
        }

        @Test
        fun `a vote drawing from participants takes no options`() {
            assertFails<ConflictException>(ErrorCode.OPTIONS_NOT_ALLOWED) { vote().addOption(CREATOR_ID, "Pizza", NOW) }
        }

        @Test
        fun `reordering renumbers the options in the given order`() {
            val vote = vote(drawTarget = DrawTarget.OPTIONS, options = listOf("A", "B", "C"))
            val reversed = vote.options.map { it.id }.reversed()

            vote.reorderOptions(CREATOR_ID, reversed)

            assertEquals(listOf("C" to 0, "B" to 1, "A" to 2), vote.options.map { it.title to it.position })
        }

        @Test
        fun `a new order must be a permutation of the options`() {
            val vote = vote(drawTarget = DrawTarget.OPTIONS, options = listOf("A", "B", "C"))
            val ids = vote.options.map { it.id }

            listOf(ids.drop(1), ids + ids[0], ids.drop(1) + UUID.randomUUID()).forEach { order ->
                assertFails<BadRequestException>(ErrorCode.OPTION_ORDER_INVALID) { vote.reorderOptions(CREATOR_ID, order) }
            }
            assertEquals(listOf("A", "B", "C"), vote.options.map { it.title })
        }

        @Test
        fun `removing an unknown option changes nothing`() {
            val vote = vote(drawTarget = DrawTarget.OPTIONS, options = listOf("A"))
            vote.removeOption(CREATOR_ID, UUID.randomUUID())
            assertEquals(listOf("A"), vote.options.map { it.title })
        }
    }

    @Nested
    inner class Draw {
        @Test
        fun `a simple draw picks among all participants without reading the history`() {
            val vote = vote(participants = listOf("only@test.com"))

            val outcome = vote.draw(CREATOR_ID, WinnerPicker(), NOW) { error("SIMPLE must not read the history") }

            val entry = outcome.entry as DrawHistory.ParticipantWinner
            assertEquals("only@test.com", entry.email?.value)
            assertEquals(vote.id, entry.voteId)
            assertEquals(1, entry.round)
            assertFalse(outcome.newRoundStarted)
            assertEquals(VoteStatus.DRAWN, vote.status)
        }

        @Test
        fun `only a pending vote can be drawn`() {
            assertFails<ConflictException>(ErrorCode.VOTE_NOT_PENDING) {
                vote(status = VoteStatus.DRAWN).draw(CREATOR_ID, WinnerPicker(), NOW, noHistory)
            }
        }

        @Test
        fun `a participants vote without participants cannot be drawn`() {
            assertFails<ConflictException>(ErrorCode.NO_PARTICIPANTS) {
                vote(participants = emptyList()).draw(CREATOR_ID, WinnerPicker(), NOW, noHistory)
            }
        }

        @Test
        fun `an options vote without options asks for one instead of drawing participants`() {
            val vote = vote(drawTarget = DrawTarget.OPTIONS)
            val ex = assertThrows<ConflictException> { vote.draw(CREATOR_ID, WinnerPicker(), NOW, noHistory) }
            assertEquals(ErrorCode.NO_OPTIONS, ex.code)
            assertEquals(VoteStatus.PENDING, vote.status)
        }

        @Test
        fun `an options vote draws an option and leaves its participants out`() {
            val vote = vote(drawTarget = DrawTarget.OPTIONS, participants = listOf(CREATOR_EMAIL, "p@test.com"), options = listOf("Pizza"))

            val entry = vote.draw(CREATOR_ID, WinnerPicker(), NOW, noHistory).entry as DrawHistory.OptionWinner

            assertEquals("Pizza", entry.optionTitle)
            assertEquals(vote.options.single().id, entry.optionId)
        }

        @Test
        fun `fair rotation picks only participants who have not won this round`() {
            val vote = vote(VoteMode.FAIR_ROTATION, participants = listOf("won@test.com", "eligible@test.com"))
            val won = DrawHistory.ParticipantWinner(voteId = vote.id, email = Email.of("won@test.com"), round = 1, drawnAt = NOW)
            // A winner who deleted their account has no address and blocks nobody.
            val anonymized = DrawHistory.ParticipantWinner(voteId = vote.id, email = null, round = 1, drawnAt = NOW)

            val outcome = vote.draw(CREATOR_ID, WinnerPicker(), NOW) { round -> if (round == 1) listOf(won, anonymized) else emptyList() }

            assertEquals("eligible@test.com", (outcome.entry as DrawHistory.ParticipantWinner).email?.value)
            assertFalse(outcome.newRoundStarted)
            assertEquals(1, vote.currentRound)
            assertEquals(listOf("eligible@test.com"), outcome.entry.audit?.candidates)
        }

        @Test
        fun `fair rotation starts a new round with everyone once all have won`() {
            val vote = vote(VoteMode.FAIR_ROTATION, participants = listOf("sole@test.com"))
            val won = DrawHistory.ParticipantWinner(voteId = vote.id, email = Email.of("sole@test.com"), round = 1, drawnAt = NOW)

            val outcome = vote.draw(CREATOR_ID, WinnerPicker(), NOW) { listOf(won) }

            assertTrue(outcome.newRoundStarted)
            assertEquals(2, vote.currentRound)
            assertEquals(2, outcome.entry.round)
        }

        @Test
        fun `fair rotation over options leaves out this round's winning options, by id`() {
            val vote = vote(VoteMode.FAIR_ROTATION, DrawTarget.OPTIONS, currentRound = 3, options = listOf("Won", "Eligible", "Also eligible"))
            val wonOption = vote.options.first()
            val won = DrawHistory.OptionWinner(voteId = vote.id, optionId = wonOption.id, optionTitle = wonOption.title, round = 3, drawnAt = NOW)
            // A deleted option keeps its title in the history but no longer blocks anything.
            val deleted = DrawHistory.OptionWinner(voteId = vote.id, optionId = null, optionTitle = "Eligible", round = 3, drawnAt = NOW)

            val outcome = vote.draw(CREATOR_ID, WinnerPicker(), NOW) { round -> if (round == 3) listOf(won, deleted) else emptyList() }

            assertEquals(
                vote.options
                    .drop(1)
                    .map { it.id.toString() }
                    .sorted(),
                outcome.entry.audit?.candidates
            )
            assertTrue((outcome.entry as DrawHistory.OptionWinner).optionTitle != "Won")
            assertFalse(outcome.newRoundStarted)
        }

        @Test
        fun `fair rotation over options starts a new round once every option has won`() {
            val vote = vote(VoteMode.FAIR_ROTATION, DrawTarget.OPTIONS, options = listOf("Sole"))
            val option = vote.options.single()

            val outcome =
                vote.draw(CREATOR_ID, WinnerPicker(), NOW) {
                    listOf(DrawHistory.OptionWinner(voteId = vote.id, optionId = option.id, optionTitle = option.title, round = 1, drawnAt = NOW))
                }

            assertTrue(outcome.newRoundStarted)
            assertEquals(2, vote.currentRound)
            assertEquals("Sole", (outcome.entry as DrawHistory.OptionWinner).optionTitle)
        }

        @Test
        fun `the recorded audit reproduces the winner`() {
            val vote = vote(participants = listOf("c@test.com", "a@test.com", "b@test.com"))

            val entry = vote.draw(CREATOR_ID, WinnerPicker(), NOW, noHistory).entry as DrawHistory.ParticipantWinner

            val audit = checkNotNull(entry.audit)
            assertEquals(listOf("a@test.com", "b@test.com", "c@test.com"), audit.candidates)
            assertEquals(entry.email?.value, audit.candidates[WinnerPicker.index(audit.seed, 3)])
        }
    }

    @Nested
    inner class Lifecycle {
        @Test
        fun `a drawn vote can be reopened, a pending one cannot`() {
            val vote = vote(status = VoteStatus.DRAWN)
            vote.reopen(CREATOR_ID)
            assertEquals(VoteStatus.PENDING, vote.status)

            assertFails<ConflictException>(ErrorCode.VOTE_NOT_DRAWN) { vote.reopen(CREATOR_ID) }
        }

        @Test
        fun `a vote closes once`() {
            val vote = vote()
            vote.close(CREATOR_ID)
            assertEquals(VoteStatus.CLOSED, vote.status)

            assertFails<ConflictException>(ErrorCode.VOTE_ALREADY_CLOSED) { vote.close(CREATOR_ID) }
        }

        @Test
        fun `the exposed lists are snapshots`() {
            val vote = vote()
            val participants = vote.participants
            vote.invite(CREATOR_ID, "new@test.com", NOW) { null }
            assertEquals(1, participants.size)
            assertNull(vote.description)
        }

        @Test
        fun `the vote records what happened, handed out once`() {
            val created = create(participantEmails = listOf("p@test.com"))
            assertEquals(listOf(VoteCreated(created.id, "Vote", CREATOR_ID, listOf(Email.of("p@test.com")))), created.pullEvents())
            assertEquals(emptyList<VoteEvent>(), created.pullEvents())

            val vote = vote()
            vote.invite(CREATOR_ID, "new@test.com", NOW) { null }
            val outcome = vote.draw(CREATOR_ID, WinnerPicker(), NOW, noHistory)
            assertEquals(
                listOf(
                    ParticipantInvited(vote.id, "Vote", CREATOR_ID, Email.of("new@test.com")),
                    VoteDrawn(vote.id, "Vote", CREATOR_ID, outcome, listOf(Email.of(CREATOR_EMAIL), Email.of("new@test.com")))
                ),
                vote.pullEvents()
            )
        }

        @Test
        fun `a refused change records nothing`() {
            val vote = vote(status = VoteStatus.DRAWN)
            assertFails<ConflictException>(ErrorCode.VOTE_NOT_PENDING) { vote.invite(CREATOR_ID, "new@test.com", NOW) { null } }
            assertFails<ConflictException>(ErrorCode.VOTE_NOT_PENDING) { vote.draw(CREATOR_ID, WinnerPicker(), NOW, noHistory) }
            assertEquals(emptyList<VoteEvent>(), vote.pullEvents())
        }

        @Test
        fun `recorded times are the given now, never the clock`() {
            val created = create(participantEmails = listOf("p@test.com"), optionTitles = listOf("A"))
            assertEquals(NOW, created.createdAt)
            assertEquals(listOf(NOW, NOW), created.participants.map { it.addedAt })
            assertEquals(NOW, created.options.single().createdAt)

            val later = NOW.plusSeconds(60)
            val vote = vote(drawTarget = DrawTarget.OPTIONS, options = listOf("A"))
            assertEquals(later, vote.invite(CREATOR_ID, "new@test.com", later) { null }.addedAt)
            assertEquals(later, vote.addOption(CREATOR_ID, "B", later).createdAt)
            assertEquals(later, vote.draw(CREATOR_ID, WinnerPicker(), later, noHistory).entry.drawnAt)
        }
    }
}
