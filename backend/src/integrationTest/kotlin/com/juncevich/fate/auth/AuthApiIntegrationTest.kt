package com.juncevich.fate.auth

import com.juncevich.fate.AbstractApiIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

class AuthApiIntegrationTest : AbstractApiIntegrationTest() {
    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    fun `POST register - valid request - returns 201 with JWT and refresh cookie`() {
        val email = "register-${UUID.randomUUID()}@test.com"

        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$email","password":"password123","displayName":"Alice"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.accessToken") { isString() }
                jsonPath("$.userId") { isString() }
                jsonPath("$.email") { value(email) }
                jsonPath("$.displayName") { value("Alice") }
                header {
                    string(
                        "Set-Cookie",
                        org.hamcrest.Matchers.containsString("fate_refresh_token=")
                    )
                }
            }
    }

    @Test
    fun `POST register - duplicate email - returns 409`() {
        val email = "dup-${UUID.randomUUID()}@test.com"
        registerAndGetToken(email)

        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$email","password":"password123","displayName":"Bob"}"""
            }.andExpect {
                status { isConflict() }
            }
    }

    @Test
    fun `POST register - invalid email format - returns 400 with field error`() {
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"not-an-email","password":"password123","displayName":"Test"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.email") { exists() }
            }
    }

    @Test
    fun `POST register - password too short - returns 400 with field error`() {
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"u@test.com","password":"short","displayName":"Test"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.password") { exists() }
            }
    }

    @Test
    fun `POST register - blank display name - returns 400 with field error`() {
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"u2@test.com","password":"password123","displayName":"   "}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.displayName") { exists() }
            }
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @Test
    fun `POST login - valid credentials - returns 200 with JWT and refresh cookie`() {
        val email = "login-${UUID.randomUUID()}@test.com"
        registerAndGetToken(email)

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$email","password":"password123"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.accessToken") { isString() }
                jsonPath("$.email") { value(email) }
                header {
                    string(
                        "Set-Cookie",
                        org.hamcrest.Matchers.containsString("fate_refresh_token=")
                    )
                }
            }
    }

    @Test
    fun `POST login - wrong password - returns 401`() {
        val email = "wrongpass-${UUID.randomUUID()}@test.com"
        registerAndGetToken(email)

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$email","password":"wrongpassword"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `POST login - unknown email - returns 401`() {
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"nobody-${UUID.randomUUID()}@test.com","password":"password123"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    // ── Refresh ────────────────────────────────────────────────────────────────

    @Test
    fun `POST refresh - valid token via body - returns 200 with new JWT and cookie`() {
        val email = "refresh-body-${UUID.randomUUID()}@test.com"
        registerAndGetToken(email)
        val (_, refreshToken) = loginAndGetTokens(email)

        mockMvc
            .post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"$refreshToken"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.accessToken") { isString() }
                header {
                    string(
                        "Set-Cookie",
                        org.hamcrest.Matchers.containsString("fate_refresh_token=")
                    )
                }
            }
    }

    @Test
    fun `POST refresh - valid token via cookie - returns 200 with new JWT`() {
        val email = "refresh-cookie-${UUID.randomUUID()}@test.com"
        registerAndGetToken(email)
        val (_, refreshToken) = loginAndGetTokens(email)

        mockMvc
            .post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
                cookie(jakarta.servlet.http.Cookie("fate_refresh_token", refreshToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.accessToken") { isString() }
            }
    }

    @Test
    fun `POST refresh - invalid token - returns 401`() {
        mockMvc
            .post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"invalid-token-that-does-not-exist"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `POST refresh - no token provided - returns 400`() {
        mockMvc
            .post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST refresh - token cannot be used twice (rotation)`() {
        val email = "refresh-once-${UUID.randomUUID()}@test.com"
        registerAndGetToken(email)
        val (_, refreshToken) = loginAndGetTokens(email)

        // First use succeeds
        mockMvc
            .post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"$refreshToken"}"""
            }.andExpect { status { isOk() } }

        // Second use with same token must fail
        mockMvc
            .post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"$refreshToken"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    @Test
    fun `POST logout - with refresh token - returns 204 with cleared cookie`() {
        val email = "logout-${UUID.randomUUID()}@test.com"
        registerAndGetToken(email)
        val (_, refreshToken) = loginAndGetTokens(email)

        mockMvc
            .post("/api/v1/auth/logout") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"$refreshToken"}"""
            }.andExpect {
                status { isNoContent() }
                header {
                    string(
                        "Set-Cookie",
                        org.hamcrest.Matchers.containsString("fate_refresh_token=;")
                    )
                }
            }

        // Refresh should now fail
        mockMvc
            .post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"$refreshToken"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `POST logout-all - authenticated - returns 204 and invalidates all tokens`() {
        val email = "logoutall-${UUID.randomUUID()}@test.com"
        registerAndGetToken(email)
        val (accessToken1, refreshToken1) = loginAndGetTokens(email)
        val (_, refreshToken2) = loginAndGetTokens(email)

        mockMvc
            .post("/api/v1/auth/logout-all") {
                header("Authorization", "Bearer $accessToken1")
            }.andExpect {
                status { isNoContent() }
            }

        // Both refresh tokens should be invalid
        mockMvc
            .post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"$refreshToken1"}"""
            }.andExpect { status { isUnauthorized() } }

        mockMvc
            .post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"$refreshToken2"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    // ── JWT filter / security ──────────────────────────────────────────────────

    @Test
    fun `protected endpoint - no JWT - returns 401`() {
        mockMvc.get("/api/v1/votes").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `protected endpoint - valid JWT - returns 200`() {
        val email = "jwt-valid-${UUID.randomUUID()}@test.com"
        val accessToken = registerAndGetToken(email)

        mockMvc
            .get("/api/v1/votes") {
                header("Authorization", "Bearer $accessToken")
            }.andExpect { status { isOk() } }
    }

    @Test
    fun `protected endpoint - tampered JWT - returns 401`() {
        val email = "jwt-tamper-${UUID.randomUUID()}@test.com"
        val accessToken = registerAndGetToken(email)

        // Corrupt the signature
        val tampered = accessToken.dropLast(5) + "XXXXX"

        mockMvc
            .get("/api/v1/votes") {
                header("Authorization", "Bearer $tampered")
            }.andExpect { status { isUnauthorized() } }
    }
}
