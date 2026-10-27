package com.juncevich.fate.account

import com.juncevich.fate.AbstractApiIntegrationTest
import com.juncevich.fate.storage.ObjectStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

/** Deleting an account end to end: what goes, what stays anonymized, what other users still see. */
class AccountDeletionIntegrationTest
    @Autowired
    constructor(
        private val objectStorage: ObjectStorage,
    ) : AbstractApiIntegrationTest() {
        private fun email(prefix: String) = "$prefix-${UUID.randomUUID()}@test.com"

        private fun createVote(
            token: String,
            participants: List<String>,
        ): String =
            parse(
                mockMvc
                    .post("/api/v1/votes") {
                        header("Authorization", "Bearer $token")
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"title":"V","mode":"SIMPLE","participantEmails":[${participants.joinToString(",") { "\"$it\"" }}]}"""
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString
            ).text("id")

        private fun count(
            sql: String,
            vararg args: Any,
        ) = jdbcTemplate.queryForObject(sql, Int::class.java, *args) ?: 0

        @Test
        fun `deleting an account removes its votes and data and anonymizes it in other votes`() {
            val annEmail = email("ann")
            val bobEmail = email("bob")
            val ann = signUp(annEmail)
            val bob = signUp(bobEmail)

            val annsVote = createVote(ann, listOf(bobEmail))
            // Bob's vote where Ann is the only participant left, so she wins its draw.
            val bobsVote = createVote(bob, listOf(annEmail))
            mockMvc.delete("/api/v1/votes/$bobsVote/participants/$bobEmail") { header("Authorization", "Bearer $bob") }
            mockMvc
                .post("/api/v1/votes/$bobsVote/draw") { header("Authorization", "Bearer $bob") }
                .andExpect { jsonPath("$.winnerEmail") { value(annEmail) } }
            assertTrue(count("SELECT count(*) FROM notification_outbox WHERE payload::jsonb ->> 'recipientEmail' = ?", annEmail) > 0)

            mockMvc
                .post("/api/v1/auth/delete-account") { header("Authorization", "Bearer $ann") }
                .andExpect { status { isNoContent() } }

            assertEquals(0, count("SELECT count(*) FROM users WHERE email = ?", annEmail))
            assertEquals(0, count("SELECT count(*) FROM votes WHERE id = ?::uuid", annsVote))
            assertEquals(0, count("SELECT count(*) FROM vote_participants WHERE email = ?", annEmail))
            assertEquals(0, count("SELECT count(*) FROM draw_history WHERE winner_email = ?", annEmail))
            assertEquals(0, count("SELECT count(*) FROM notification_outbox WHERE payload::jsonb ->> 'recipientEmail' = ?", annEmail))

            // Bob still sees his vote and its history, with the winner anonymized.
            mockMvc.get("/api/v1/votes/$bobsVote/history/page") { header("Authorization", "Bearer $bob") }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].winnerDisplayName") { value("Deleted user") }
                jsonPath("$.content[0].winnerEmail") { value(null as Any?) }
            }
            // Her token, still valid for minutes, doesn't bring the account back.
            mockMvc.get("/api/v1/users/me") { header("Authorization", "Bearer $ann") }.andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `the avatar object goes once the deletion has committed`() {
            val token = signUp(email("pic"))
            val image = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "png", it) }
            mockMvc
                .multipart(HttpMethod.PUT, "/api/v1/users/me/avatar") {
                    file(MockMultipartFile("file", "a.png", "image/png", image.toByteArray()))
                    header("Authorization", "Bearer $token")
                }.andExpect { status { isOk() } }
            val userId =
                parse(
                    mockMvc
                        .get("/api/v1/users/me") { header("Authorization", "Bearer $token") }
                        .andReturn()
                        .response.contentAsString
                ).text("userId")
            assertNotNull(objectStorage.get("avatars/$userId.png"))

            mockMvc.post("/api/v1/auth/delete-account") { header("Authorization", "Bearer $token") }.andExpect { status { isNoContent() } }

            assertNull(objectStorage.get("avatars/$userId.png"))
        }

        @Test
        fun `the stats tell how many votes deleting the account would delete`() {
            val token = signUp(email("stats"))
            createVote(token, emptyList())
            createVote(token, emptyList())

            mockMvc
                .get("/api/v1/votes/stats") { header("Authorization", "Bearer $token") }
                .andExpect { jsonPath("$.createdCount") { value(2) } }
        }
    }
