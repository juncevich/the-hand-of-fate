package com.juncevich.fate

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/** Every error body carries a `code` clients can translate, whoever produced it. */
class ErrorCodesIntegrationTest : AbstractApiIntegrationTest() {
    @Test
    fun `bean validation reports the violated constraint per field`() {
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"nope","password":"short","displayName":""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.fieldErrors.email") { value("Email") }
                jsonPath("$.fieldErrors.password") { value("Size") }
                jsonPath("$.fieldErrors.displayName") { value("NotBlank") }
            }
    }

    @Test
    fun `spring mvc errors get a generic code`() {
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = "{not json"
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("REQUEST_INVALID") }
            }
    }

    @Test
    fun `wrong credentials and domain errors carry their own codes`() {
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"ghost-${UUID.randomUUID()}@test.com","password":"password123"}"""
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_CREDENTIALS") }
            }

        val token = registerAndGetToken("codes-${UUID.randomUUID()}@test.com")
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
