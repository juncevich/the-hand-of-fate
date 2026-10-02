package com.juncevich.fate

import com.juncevich.fate.auth.TelegramLinkService
import com.juncevich.fate.auth.UserQueryService
import com.juncevich.fate.auth.internal.service.AuthService
import com.juncevich.fate.auth.internal.service.RegisterRequest
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.vote.CreateVoteCommand
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConsistencyIntegrationTest
    @Autowired
    constructor(
        private val votes: VoteService,
        private val auth: AuthService,
        private val telegram: TelegramLinkService,
        private val users: UserQueryService,
        private val jdbc: JdbcTemplate,
        private val transactions: PlatformTransactionManager,
    ) : AbstractApiIntegrationTest() {
        private fun newUser() = auth.register(RegisterRequest("${UUID.randomUUID()}@test.com", "password123", "Test"))

        private fun newVote(userId: UUID) = votes.createVote(userId, CreateVoteCommand("Vote", null, VoteMode.FAIR_ROTATION, emptyList(), listOf("Winner")))

        @Test
        fun `deleted winning option remains readable in details and history`() {
            val userId = UUID.fromString(newUser().response.userId)
            val vote = newVote(userId)
            votes.draw(vote.id, userId)
            votes.reopen(vote.id, userId)
            votes.removeOption(vote.id, userId, vote.options.single().id)
            val email = checkNotNull(users.findById(userId)).email

            assertEquals("Winner", votes.getVote(vote.id, userId, email).lastResult?.winnerOptionTitle)
            assertEquals("Winner", votes.getHistory(vote.id, userId, email).single().winnerOptionTitle)
            assertEquals("Winner", votes.getLastResult(vote.id, userId, email)?.winnerOptionTitle)
        }

        @ParameterizedTest
        @ValueSource(strings = ["addParticipant", "removeParticipant", "addOption", "removeOption"])
        fun `composition changes wait for draw and reject the committed DRAWN status`(operation: String) {
            val userId = UUID.fromString(newUser().response.userId)
            val vote = newVote(userId)
            val email = checkNotNull(users.findById(userId)).email
            val (draw, change) =
                compete(
                    first = { votes.draw(vote.id, userId) },
                    second = {
                        when (operation) {
                            "addParticipant" -> votes.addParticipant(vote.id, userId, "new@test.com")
                            "removeParticipant" -> votes.removeParticipant(vote.id, userId, email)
                            "addOption" -> votes.addOption(vote.id, userId, "New")
                            else -> votes.removeOption(vote.id, userId, vote.options.single().id)
                        }
                    }
                )
            assertTrue(draw.isSuccess)
            assertTrue(change.exceptionOrNull() is IllegalStateException)
            val detail = votes.getVote(vote.id, userId, email)
            assertEquals(listOf("Winner"), detail.options.map { it.title })
            assertEquals(listOf(email), detail.participants.map { it.email })
        }

        @Test
        fun `parallel refresh consumes token once and rejects the losing request as unauthorized`() {
            val token = newUser().refreshToken
            val (first, second) = compete({ auth.refresh(token) }, { auth.refresh(token) })
            assertTrue(first.isSuccess)
            assertTrue(second.exceptionOrNull() is BadCredentialsException)
        }

        @Test
        fun `parallel telegram linking consumes token once`() {
            val userId = UUID.fromString(newUser().response.userId)
            val token = telegram.generateLinkToken(userId).token
            val firstId = UUID.randomUUID().mostSignificantBits
            val secondId = UUID.randomUUID().mostSignificantBits
            val (first, second) =
                compete(
                    { telegram.linkAccount(token, firstId, "First") },
                    { telegram.linkAccount(token, secondId, "Second") }
                )
            assertTrue(first.isSuccess)
            assertTrue(second.exceptionOrNull() is NoSuchElementException)
            assertEquals(firstId, users.findById(userId)?.telegramId)
        }

        @Test
        fun `expired refresh and link tokens are deleted despite rejecting the request`() {
            val tokens = newUser()
            val userId = UUID.fromString(tokens.response.userId)
            jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute' WHERE user_id = ?", userId)
            assertThrows<BadCredentialsException> { auth.refresh(tokens.refreshToken) }
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Int::class.java, userId))

            val link = telegram.generateLinkToken(userId)
            jdbc.update("UPDATE telegram_link_tokens SET expires_at = now() - interval '1 minute' WHERE user_id = ?", userId)
            assertThrows<BadRequestException> { telegram.linkAccount(link.token, 42, "Expired") }
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM telegram_link_tokens WHERE user_id = ?", Int::class.java, userId))
        }

        @Test
        fun `normalized duplicate registration returns conflict`() {
            val email = "${UUID.randomUUID()}@test.com"
            auth.register(RegisterRequest(email, "password123", "Test"))
            assertThrows<IllegalStateException> { auth.register(RegisterRequest(" ${email.uppercase()} ", "password123", "Test")) }
        }

        @Test
        fun `participants are normalized deduplicated and can access the vote`() {
            val creator = newUser()
            val participant = newUser()
            val creatorId = UUID.fromString(creator.response.userId)
            val email = participant.response.email
            val vote =
                votes.createVote(
                    creatorId,
                    CreateVoteCommand(" Vote ", null, VoteMode.SIMPLE, listOf(" ${email.uppercase()} ", email, creator.response.email), null)
                )
            assertEquals("Vote", vote.title)
            assertEquals(setOf(creator.response.email, email), vote.participants.map { it.email }.toSet())
            assertEquals(vote.id, votes.getVote(vote.id, UUID.fromString(participant.response.userId), email).id)
        }

        @Test
        fun `history pages retain totals and have stable order for equal timestamps`() {
            val userId = UUID.fromString(newUser().response.userId)
            val vote = newVote(userId)
            repeat(3) { index ->
                votes.draw(vote.id, userId)
                if (index < 2) votes.reopen(vote.id, userId)
            }
            jdbc.update("UPDATE draw_history SET drawn_at = '2026-01-01T00:00:00Z' WHERE vote_id = ?", vote.id)
            val email = checkNotNull(users.findById(userId)).email
            val first = votes.getHistory(vote.id, userId, email, PageRequest.of(0, 2))
            val last = votes.getHistory(vote.id, userId, email, PageRequest.of(1, 2))
            assertEquals(3L, first.totalElements)
            assertEquals(2, first.totalPages)
            assertEquals(2, first.content.size)
            assertEquals(1, last.content.size)
            assertEquals(3, (first.content + last.content).map { it.id }.toSet().size)
            assertEquals(first.content.map { it.id }, votes.getHistory(vote.id, userId, email, PageRequest.of(0, 2)).content.map { it.id })
            assertThrows<IllegalArgumentException> { votes.getHistory(vote.id, userId, email, PageRequest.of(0, 101)) }

            val token = auth.login(email, "password123").response.accessToken
            mockMvc
                .get("/api/v1/votes/${vote.id}/history/page?page=1&size=2") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.content.length()") { value(1) }
                    jsonPath("$.totalElements") { value(3) }
                    jsonPath("$.number") { value(1) }
                }
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "long"])
        fun `service rejects invalid option titles both on creation and addition`(input: String) {
            val title = if (input == "long") "x".repeat(256) else input
            val userId = UUID.fromString(newUser().response.userId)
            assertThrows<BadRequestException> {
                votes.createVote(userId, CreateVoteCommand("Vote", null, VoteMode.SIMPLE, emptyList(), listOf(title)))
            }
            val vote = newVote(userId)
            assertThrows<BadRequestException> { votes.addOption(vote.id, userId, title) }
        }

        @Test
        fun `duplicate option returns conflict and leaves vote intact`() {
            val tokens = newUser()
            val userId = UUID.fromString(tokens.response.userId)
            val vote = newVote(userId)
            mockMvc
                .post("/api/v1/votes/${vote.id}/options") {
                    header("Authorization", "Bearer ${tokens.response.accessToken}")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"title":" Winner "}"""
                }.andExpect { status { isConflict() } }
            assertEquals(listOf("Winner"), votes.getVote(vote.id, userId, tokens.response.email).options.map { it.title })
        }

        @Test
        fun `email migration merges legacy aliases and preserves historical wins`() {
            val creator = newUser()
            val participant = newUser()
            val userId = UUID.fromString(creator.response.userId)
            val vote = newVote(userId)
            val email = participant.response.email
            val insert = "INSERT INTO vote_participants (vote_id, email) VALUES (?, ?)"
            jdbc.update(insert, vote.id, " ${email.uppercase()} ")
            jdbc.update(insert, vote.id, email)
            jdbc.update("INSERT INTO draw_history (vote_id, winner_email, round) VALUES (?, ?, 1)", vote.id, " ${email.uppercase()} ")
            val resource =
                org.springframework.core.io
                    .ClassPathResource("db/migration/V13__normalize_participant_emails.sql")
            val migration = resource.inputStream.bufferedReader().use { it.readText() }
            jdbc.execute(migration)
            val detail = votes.getVote(vote.id, UUID.fromString(participant.response.userId), email)
            assertEquals(1, detail.participants.count { it.email == email })
            assertEquals(email, detail.lastResult?.winnerEmail)
        }

        /** Holds the first transaction open until PostgreSQL confirms the competing writer is blocked. */
        private fun compete(
            first: () -> Any?,
            second: () -> Any?,
        ): Pair<Result<Any?>, Result<Any?>> {
            val acquired = CountDownLatch(1)
            val release = CountDownLatch(1)
            return Executors.newFixedThreadPool(2).use { executor ->
                val winner =
                    executor.submit<Result<Any?>> {
                        runCatching {
                            TransactionTemplate(transactions).execute {
                                val result = first()
                                acquired.countDown()
                                check(release.await(10, TimeUnit.SECONDS)) { "Writer release timed out" }
                                result
                            }
                        }
                    }
                try {
                    assertTrue(acquired.await(10, TimeUnit.SECONDS), "First transaction did not acquire its lock")
                    val contender = executor.submit<Result<Any?>> { runCatching { second() } }
                    awaitBlockedWriter()
                    release.countDown()
                    winner.get(10, TimeUnit.SECONDS) to contender.get(10, TimeUnit.SECONDS)
                } finally {
                    release.countDown()
                }
            }
        }

        private fun awaitBlockedWriter() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                val waiting =
                    jdbc.queryForObject(
                        "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'",
                        Int::class.java
                    ) ?: 0
                if (waiting > 0) return
                Thread.sleep(20)
            }
            fail<Unit>("Competing writer did not wait for the first transaction")
        }
    }
