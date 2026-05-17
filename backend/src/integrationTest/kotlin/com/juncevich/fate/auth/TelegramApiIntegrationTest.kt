package com.juncevich.fate.auth

import com.juncevich.fate.AbstractApiIntegrationTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import java.util.UUID

class TelegramApiIntegrationTest : AbstractApiIntegrationTest() {
    @Test
    fun `GET telegram link-token - authenticated - returns token and expiresAt`() {
        val token = registerAndGetToken("tg-${UUID.randomUUID()}@test.com")

        val result =
            mockMvc
                .get("/api/v1/telegram/link-token") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.token") { isString() }
                    jsonPath("$.expiresAt") { isString() }
                }.andReturn()

        val body = parse(result.response.contentAsString)
        val linkToken = body["token"].asText()
        assertTrue(linkToken.isNotBlank(), "Link token must not be blank")
        assertNotNull(body["expiresAt"], "expiresAt must be present")
    }

    @Test
    fun `GET telegram link-token - unauthenticated - returns 401`() {
        mockMvc.get("/api/v1/telegram/link-token").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `DELETE telegram unlink - no linked account - returns 409`() {
        val token = registerAndGetToken("tg-unlink-${UUID.randomUUID()}@test.com")

        // Unlinking without a linked Telegram account is a conflict
        mockMvc
            .delete("/api/v1/telegram/unlink") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `DELETE telegram unlink - unauthenticated - returns 401`() {
        mockMvc.delete("/api/v1/telegram/unlink").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `GET telegram link-token - generates distinct token on each call`() {
        val token = registerAndGetToken("tg-distinct-${UUID.randomUUID()}@test.com")

        val result1 =
            mockMvc
                .get("/api/v1/telegram/link-token") {
                    header("Authorization", "Bearer $token")
                }.andReturn()
        val result2 =
            mockMvc
                .get("/api/v1/telegram/link-token") {
                    header("Authorization", "Bearer $token")
                }.andReturn()

        val token1 = parse(result1.response.contentAsString)["token"].asText()
        val token2 = parse(result2.response.contentAsString)["token"].asText()

        assertTrue(token1 != token2, "Each link token call must return a unique token")
    }
}
