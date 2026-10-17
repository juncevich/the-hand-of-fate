package com.juncevich.fate.auth

import com.juncevich.fate.AbstractApiIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/** Email verification, password reset and password change, end to end on PostgreSQL. */
class AccountApiIntegrationTest : AbstractApiIntegrationTest() {
    private fun newEmail() = "account-${UUID.randomUUID()}@test.com"

    private fun post(
        path: String,
        json: String,
        bearer: String? = null,
    ): ResultActionsDsl =
        mockMvc.post("/api/v1/auth/$path") {
            contentType = MediaType.APPLICATION_JSON
            content = json
            bearer?.let { header("Authorization", "Bearer $it") }
        }

    private fun register(email: String) =
        post("register", """{"email":"$email","password":"password123","displayName":"Ann"}""")
            .andExpect { status { isAccepted() } }

    private fun login(
        email: String,
        password: String = "password123",
    ) = post("login", """{"email":"$email","password":"$password"}""")

    private fun queued(
        email: String,
        type: String,
    ): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM notification_outbox WHERE event_type = ? AND payload::jsonb ->> 'recipientEmail' = ?",
            Int::class.java,
            type,
            email
        ) ?: 0

    @Test
    fun `an unverified account cannot sign in until it follows the mailed link`() {
        val email = newEmail()
        register(email)

        login(email).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
        }
        // A wrong password still reads as wrong credentials, revealing nothing about the account.
        login(email, "wrong-password").andExpect { status { isUnauthorized() } }

        val token = mailedToken(email, "EMAIL_VERIFICATION")
        post("verify-email", """{"token":"$token"}""").andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isString() }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("fate_refresh_token=")) }
        }
        login(email).andExpect { status { isOk() } }

        post("verify-email", """{"token":"$token"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
    }

    @Test
    fun `an expired verification link is rejected and spent`() {
        val email = newEmail()
        register(email)
        val token = mailedToken(email, "EMAIL_VERIFICATION")
        jdbcTemplate.update(
            "UPDATE email_tokens SET expires_at = now() - interval '1 minute' WHERE user_id = (SELECT id FROM users WHERE email = ?)",
            email
        )

        post("verify-email", """{"token":"$token"}""").andExpect { jsonPath("$.code") { value("TOKEN_EXPIRED") } }
        post("verify-email", """{"token":"$token"}""").andExpect { jsonPath("$.code") { value("INVALID_TOKEN") } }
    }

    @Test
    fun `resending is rate limited per account and silent about unknown or verified addresses`() {
        val email = newEmail()
        register(email)

        post("resend-verification", """{"email":"$email"}""").andExpect { status { isAccepted() } }
        assertEquals(1, queued(email, "EMAIL_VERIFICATION"), "within the cooldown no second link is mailed")

        jdbcTemplate.update("UPDATE email_tokens SET created_at = now() - interval '2 minutes'")
        post("resend-verification", """{"email":"${email.uppercase()}"}""").andExpect { status { isAccepted() } }
        assertEquals(2, queued(email, "EMAIL_VERIFICATION"))

        val unknown = newEmail()
        post("resend-verification", """{"email":"$unknown"}""").andExpect { status { isAccepted() } }
        assertEquals(0, queued(unknown, "EMAIL_VERIFICATION"))
    }

    @Test
    fun `a password reset sets the new password and ends every session`() {
        val email = newEmail()
        registerAndGetToken(email)
        val (_, refreshToken) = loginAndGetTokens(email)

        post("forgot-password", """{"email":"$email"}""").andExpect { status { isAccepted() } }
        post("forgot-password", """{"email":"${newEmail()}"}""").andExpect { status { isAccepted() } }
        val token = mailedToken(email, "PASSWORD_RESET")

        post("reset-password", """{"token":"$token","newPassword":"new-password-1"}""")
            .andExpect { status { isNoContent() } }

        login(email).andExpect { status { isUnauthorized() } }
        login(email, "new-password-1").andExpect { status { isOk() } }
        post("refresh", """{"refreshToken":"$refreshToken"}""").andExpect { status { isUnauthorized() } }
        post("reset-password", """{"token":"$token","newPassword":"another-pass"}""")
            .andExpect { jsonPath("$.code") { value("INVALID_TOKEN") } }
    }

    @Test
    fun `resetting the password of an unverified account also confirms it`() {
        val email = newEmail()
        register(email)
        post("forgot-password", """{"email":"$email"}""")

        post("reset-password", """{"token":"${mailedToken(email, "PASSWORD_RESET")}","newPassword":"new-password-1"}""")
            .andExpect { status { isNoContent() } }

        login(email, "new-password-1").andExpect { status { isOk() } }
    }

    @Test
    fun `changing the password keeps the caller signed in and ends the other sessions`() {
        val email = newEmail()
        val accessToken = registerAndGetToken(email)
        val (_, otherSession) = loginAndGetTokens(email)

        post("change-password", """{"currentPassword":"wrong-password","newPassword":"new-password-1"}""", accessToken)
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("WRONG_PASSWORD") }
            }
        val newAccess =
            parse(
                post("change-password", """{"currentPassword":"password123","newPassword":"new-password-1"}""", accessToken)
                    .andExpect { status { isOk() } }
                    .andReturn()
                    .response.contentAsString
            ).text("accessToken")

        post("refresh", """{"refreshToken":"$otherSession"}""").andExpect { status { isUnauthorized() } }
        login(email, "new-password-1").andExpect { status { isOk() } }
        mockMvc.get("/api/v1/votes") { header("Authorization", "Bearer $newAccess") }.andExpect { status { isOk() } }
    }

    @Test
    fun `changing the password requires a session`() {
        post("change-password", """{"currentPassword":"password123","newPassword":"new-password-1"}""")
            .andExpect { status { isUnauthorized() } }
    }
}
