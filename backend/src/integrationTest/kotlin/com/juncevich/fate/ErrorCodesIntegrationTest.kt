package com.juncevich.fate

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/** Every error body carries a `code` clients can translate, whoever produced it. */
class ErrorCodesIntegrationTest : AbstractApiIntegrationTest() {
    private val token by lazy { signUp("codes-${UUID.randomUUID()}@test.com") }

    @Test
    fun `bean validation reports the violated constraint per field`() {
        mockMvc
            .post("/api/v1/votes") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"","mode":"SIMPLE","description":"${"d".repeat(2001)}"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.fieldErrors.title") { value("NotBlank") }
                jsonPath("$.fieldErrors.description") { value("Size") }
            }
    }

    @Test
    fun `spring mvc errors get a generic code`() {
        mockMvc
            .post("/api/v1/votes") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = "{not json"
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("REQUEST_INVALID") }
            }
    }

    @Test
    fun `a rejected token and domain errors carry their own codes`() {
        mockMvc
            .get("/api/v1/users/me") { header("Authorization", "Bearer not-a-jwt") }
            .andExpect { status { isUnauthorized() } }

        mockMvc
            .get("/api/v1/votes/${UUID.randomUUID()}") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("VOTE_NOT_FOUND") }
            }
        mockMvc
            .post("/api/v1/votes") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"T","mode":"SIMPLE","drawTarget":"PARTICIPANTS","options":["A"]}"""
            }.andExpect { jsonPath("$.code") { value("OPTIONS_NOT_ALLOWED") } }
    }
}
