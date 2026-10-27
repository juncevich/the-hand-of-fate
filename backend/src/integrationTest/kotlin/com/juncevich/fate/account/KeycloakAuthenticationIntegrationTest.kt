package com.juncevich.fate.account

import com.juncevich.fate.AbstractApiIntegrationTest
import com.juncevich.fate.FakeKeycloak
import com.juncevich.fate.account.deletion.IdentityDeletionWorker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Keycloak's access tokens (docs/adr/0001) against the real security chain and database. */
class KeycloakAuthenticationIntegrationTest
    @Autowired
    constructor(
        private val identityDeletionWorker: IdentityDeletionWorker,
    ) : AbstractApiIntegrationTest() {
        private fun email(prefix: String) = "$prefix-${UUID.randomUUID()}@test.com"

        private fun count(
            sql: String,
            vararg args: Any,
        ) = jdbcTemplate.queryForObject<Int>(sql, *args) ?: 0

        private fun preferences(token: String) = mockMvc.get("/api/v1/users/me/email-preferences") { bearer(token) }

        private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(token: String) {
            header("Authorization", "Bearer $token")
        }

        @BeforeEach
        fun resetKeycloak() = FakeKeycloak.reset()

        @Test
        fun `the first request with a Keycloak token creates the account under the token's subject`() {
            val id = UUID.randomUUID()
            val email = email("kate")

            preferences(keycloakToken(email.uppercase(), id)).andExpect { status { isOk() } }

            val row = jdbcTemplate.queryForMap("SELECT email, display_name FROM users WHERE id = ?", id)
            assertEquals(email, row["email"], "stored canonical")
            assertEquals("Kate Keycloak", row["display_name"])
            // Later requests find it.
            preferences(keycloakToken(email, id)).andExpect { status { isOk() } }
            assertEquals(1, count("SELECT count(*) FROM users WHERE email = ?", email))
        }

        @Test
        fun `an account with the same email under another id is taken over with its data`() {
            val email = email("legacy")
            val legacyToken = signUp(email)
            mockMvc
                .post("/api/v1/votes") {
                    bearer(legacyToken)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"title":"Before Keycloak","mode":"SIMPLE","participantEmails":["$email"]}"""
                }.andExpect { status { isCreated() } }
            val keycloakId = UUID.randomUUID()

            val token = keycloakToken(email, keycloakId)
            mockMvc
                .get("/api/v1/votes") { bearer(token) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.content[0].title") { value("Before Keycloak") }
                }

            assertEquals(keycloakId, jdbcTemplate.queryForObject<UUID>("SELECT id FROM users WHERE email = ?", email))
            // References followed the new id (ON UPDATE CASCADE).
            assertEquals(1, count("SELECT count(*) FROM votes WHERE creator_id = ?", keycloakId))
        }

        @Test
        fun `tokens Keycloak did not issue for this API are rejected`() {
            val email = email("forged")
            val id = UUID.randomUUID()
            val invalid =
                mapOf(
                    "another audience" to FakeKeycloak.token(id, email, audience = listOf("account")),
                    "unverified email" to FakeKeycloak.token(id, email, emailVerified = false),
                    "expired" to FakeKeycloak.token(id, email, expiresIn = Duration.ofMinutes(-5)),
                    "unknown key" to FakeKeycloak.token(id, email, key = FakeKeycloak.foreignKey()),
                    "another issuer" to FakeKeycloak.token(id, email, issuer = "http://elsewhere/realms/fate"),
                    "malformed" to "not-a-jwt"
                )

            invalid.forEach { (case, token) ->
                preferences(token).andExpect { status { isUnauthorized() } }
                assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", id), case)
            }
        }

        @Test
        fun `deleting the account after a recent sign-in stops its tokens`() {
            val email = email("leaving")
            val id = UUID.randomUUID()
            val token = keycloakToken(email, id)
            preferences(token).andExpect { status { isOk() } }

            mockMvc
                .post("/api/v1/auth/delete-account") {
                    bearer(token)
                }.andExpect { status { isNoContent() } }

            assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", id))
            // The token lives on for minutes, but must not bring the account back.
            preferences(token).andExpect { status { isUnauthorized() } }
            assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", id))

            identityDeletionWorker.run()

            assertTrue("DELETE $id" in FakeKeycloak.adminCalls)
            assertTrue(id !in FakeKeycloak.users)
            assertEquals(1, count("SELECT count(*) FROM deleted_accounts WHERE user_id = ? AND identity_deleted_at IS NOT NULL", id))
        }

        @Test
        fun `deleting the account after an old sign-in asks to sign in again`() {
            val token = keycloakToken(email("stale"), authTime = Instant.now().minus(Duration.ofHours(1)))

            mockMvc
                .post("/api/v1/auth/delete-account") {
                    bearer(token)
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.code") { value("REAUTHENTICATION_REQUIRED") }
                }
        }

        @Test
        fun `a failed Keycloak deletion is retried until it succeeds`() {
            val id = UUID.randomUUID()
            val token = keycloakToken(email("retry"), id)
            FakeKeycloak.failingDeletes += id
            mockMvc
                .post("/api/v1/auth/delete-account") {
                    bearer(token)
                }.andExpect { status { isNoContent() } }

            identityDeletionWorker.run()
            assertTrue(id in FakeKeycloak.users, "still there after the failure")
            assertEquals(1, count("SELECT count(*) FROM deleted_accounts WHERE user_id = ? AND identity_deleted_at IS NULL", id))

            FakeKeycloak.failingDeletes -= id
            identityDeletionWorker.run()
            assertTrue(id !in FakeKeycloak.users)
        }

        @Test
        fun `an account Keycloak never held counts as deleted there`() {
            val id = UUID.randomUUID()
            val legacyToken = keycloakToken(email("legacy-leaving"), id)
            FakeKeycloak.users -= id

            mockMvc
                .post("/api/v1/auth/delete-account") {
                    bearer(legacyToken)
                }.andExpect { status { isNoContent() } }
            identityDeletionWorker.run()

            assertTrue("DELETE $id" in FakeKeycloak.adminCalls)
            assertEquals(1, count("SELECT count(*) FROM deleted_accounts WHERE user_id = ? AND identity_deleted_at IS NOT NULL", id))
        }

        @Test
        fun `logging out everywhere also ends the Keycloak sessions`() {
            val id = UUID.randomUUID()
            val token = keycloakToken(email("everywhere"), id)

            mockMvc.post("/api/v1/auth/logout-all") { bearer(token) }.andExpect { status { isNoContent() } }

            assertTrue("LOGOUT $id" in FakeKeycloak.adminCalls)
        }
    }
