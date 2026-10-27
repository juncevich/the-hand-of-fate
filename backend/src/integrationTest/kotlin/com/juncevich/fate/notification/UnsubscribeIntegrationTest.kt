package com.juncevich.fate.notification

import com.juncevich.fate.AbstractApiIntegrationTest
import com.juncevich.fate.shared.Email
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

class UnsubscribeIntegrationTest
    @Autowired
    constructor(
        private val preferences: EmailPreferences,
    ) : AbstractApiIntegrationTest() {
        private fun preferencesOf(token: String) = mockMvc.get("/api/v1/users/me/email-preferences") { header("Authorization", "Bearer $token") }

        @Test
        fun `a signed link unsubscribes without signing in, and the account can opt back in`() {
            val email = "unsub-${UUID.randomUUID()}@test.com"
            val token = signUp(email)
            preferencesOf(token).andExpect { jsonPath("$.notificationsEnabled") { value(true) } }

            mockMvc
                .post("/api/v1/notifications/unsubscribe") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","signature":"${preferences.sign(Email.of("someone@else.com"))}"}"""
                }.andExpect { jsonPath("$.code") { value("INVALID_TOKEN") } }
            mockMvc
                .post("/api/v1/notifications/unsubscribe/one-click?email=$email&sig=${preferences.sign(Email.of(email))}") {
                    contentType = MediaType.APPLICATION_FORM_URLENCODED
                    content = "List-Unsubscribe=One-Click"
                }.andExpect { status { isNoContent() } }

            preferencesOf(token).andExpect { jsonPath("$.notificationsEnabled") { value(false) } }
            mockMvc
                .put("/api/v1/users/me/email-preferences") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"notificationsEnabled":true}"""
                }.andExpect { jsonPath("$.notificationsEnabled") { value(true) } }
        }

        @Test
        fun `an address without an account can unsubscribe too`() {
            val email = "stranger-${UUID.randomUUID()}@test.com"

            mockMvc
                .post("/api/v1/notifications/unsubscribe") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","signature":"${preferences.sign(Email.of(email))}"}"""
                }.andExpect { status { isNoContent() } }
        }

        @Test
        fun `an expired session token does not get in the way of the public link`() {
            val email = "expired-${UUID.randomUUID()}@test.com"

            mockMvc
                .post("/api/v1/notifications/unsubscribe") {
                    header("Authorization", "Bearer not-a-valid-jwt")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","signature":"${preferences.sign(Email.of(email))}"}"""
                }.andExpect { status { isNoContent() } }
        }
    }
