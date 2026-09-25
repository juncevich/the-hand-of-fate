package com.juncevich.fate.auth

import com.juncevich.fate.AbstractApiIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pins how bearer access tokens are validated, independent of the JWT library in use.
 * Tokens are assembled by hand (HS256 over the configured secret) so every negative case
 * differs from an accepted token in exactly one respect.
 */
class JwtAuthenticationIntegrationTest
    @Autowired
    constructor(
        @param:Value("\${jwt.access-secret}") private val secret: String,
    ) : AbstractApiIntegrationTest() {
        private val b64 = Base64.getUrlEncoder().withoutPadding()

        private fun sign(
            headerJson: String,
            payloadJson: String,
            key: String = secret,
        ): String {
            val signingInput = b64.encodeToString(headerJson.toByteArray()) + "." + b64.encodeToString(payloadJson.toByteArray())
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }
            return signingInput + "." + b64.encodeToString(mac.doFinal(signingInput.toByteArray()))
        }

        private fun token(
            sub: String = UUID.randomUUID().toString(),
            email: String? = "jwt-${UUID.randomUUID()}@test.com",
            exp: Instant = Instant.now().plusSeconds(600),
            key: String = secret,
        ): String {
            val now = Instant.now().epochSecond
            val emailClaim = email?.let { ""","email":"$it"""" }.orEmpty()
            return sign(
                """{"alg":"HS256","typ":"JWT"}""",
                """{"sub":"$sub"$emailClaim,"iat":$now,"exp":${exp.epochSecond}}""",
                key
            )
        }

        private fun expectVotesStatus(
            bearer: String,
            expected: Int,
        ) {
            mockMvc
                .get("/api/v1/votes") { header("Authorization", "Bearer $bearer") }
                .andExpect { status { isEqualTo(expected) } }
        }

        @Test
        fun `well-formed HS256 token signed with the configured secret is accepted`() {
            expectVotesStatus(token(), 200)
        }

        @Test
        fun `principal is taken from the sub and email claims`() {
            val email = "owner-${UUID.randomUUID()}@test.com"
            val accessToken = registerAndGetToken(email)
            val userId =
                parse(
                    mockMvc
                        .post("/api/v1/auth/login") {
                            contentType = MediaType.APPLICATION_JSON
                            content = """{"email":"$email","password":"password123"}"""
                        }.andReturn()
                        .response.contentAsString
                ).text("userId")
            // A vote created with the app-issued token must be visible as "mine" to a hand-made
            // token carrying the same sub/email: identity comes from the claims, nothing else.
            mockMvc
                .post("/api/v1/votes") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"title":"Mine","mode":"SIMPLE","participantEmails":[]}"""
                }.andExpect { status { isCreated() } }

            mockMvc
                .get("/api/v1/votes") { header("Authorization", "Bearer ${token(sub = userId, email = email)}") }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.content[0].title") { value("Mine") }
                    jsonPath("$.content[0].isCreator") { value(true) }
                }
        }

        @Test
        fun `expired token is rejected`() {
            expectVotesStatus(token(exp = Instant.now().minusSeconds(600)), 401)
        }

        @Test
        fun `token signed with another key is rejected`() {
            expectVotesStatus(token(key = "another-secret-key-that-is-at-least-256-bits-long"), 401)
        }

        @Test
        fun `unsigned alg none token is rejected`() {
            val valid = token()
            val payload = valid.split(".")[1]
            val none = b64.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray()) + "." + payload + "."
            expectVotesStatus(none, 401)
        }

        @Test
        fun `token without email claim is rejected`() {
            expectVotesStatus(token(email = null), 401)
        }

        @Test
        fun `token whose subject is not a UUID is rejected`() {
            expectVotesStatus(token(sub = "not-a-uuid"), 401)
        }

        @Test
        fun `garbage bearer value is rejected`() {
            expectVotesStatus("not.a.jwt", 401)
        }

        @Test
        fun `stale bearer token does not block public auth endpoints`() {
            // The SPA sends its (possibly expired) access token on every apiClient call,
            // including POST /auth/logout; that must still succeed.
            mockMvc
                .post("/api/v1/auth/logout") {
                    header("Authorization", "Bearer ${token(exp = Instant.now().minusSeconds(600))}")
                }.andExpect { status { isNoContent() } }
        }
    }
