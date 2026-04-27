package com.juncevich.fate.web.auth

import com.juncevich.fate.security.AuthenticatedUser
import com.juncevich.fate.service.AuthService
import com.juncevich.fate.web.common.ErrorHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class AuthControllerTest {

    private val authService = mockk<AuthService>()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(AuthController(authService))
            .setControllerAdvice(ErrorHandler())
            .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun authResponse() = AuthResponse(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        userId = UUID.randomUUID().toString(),
        email = "user@test.com",
        displayName = "User",
    )

    private fun setAuth(id: UUID = UUID.randomUUID()) {
        val principal = AuthenticatedUser(id, "user@test.com")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
    }

    // ── POST /register ────────────────────────────────────────────────────────

    @Test
    fun `POST register - valid body - returns 201`() {
        every { authService.register(any()) } returns authResponse()

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@test.com","password":"password123","displayName":"User"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.accessToken") { value("access-token") }
        }
    }

    @Test
    fun `POST register - invalid email - returns 400 with field errors`() {
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"not-an-email","password":"password123","displayName":"User"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.email") { exists() }
        }
    }

    @Test
    fun `POST register - short password - returns 400`() {
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@test.com","password":"short","displayName":"User"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.password") { exists() }
        }
    }

    @Test
    fun `POST register - blank display name - returns 400`() {
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@test.com","password":"password123","displayName":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.displayName") { exists() }
        }
    }

    // ── POST /login ───────────────────────────────────────────────────────────

    @Test
    fun `POST login - valid credentials - returns 200`() {
        every { authService.login(any(), any()) } returns authResponse()

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@test.com","password":"password123"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value("access-token") }
        }
    }

    @Test
    fun `POST login - blank password - returns 400`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@test.com","password":""}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ── POST /refresh ─────────────────────────────────────────────────────────

    @Test
    fun `POST refresh - valid token - returns 200`() {
        every { authService.refresh(any()) } returns authResponse()

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"some-token"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    // ── POST /logout ──────────────────────────────────────────────────────────

    @Test
    fun `POST logout - valid token - returns 204`() {
        every { authService.logout(any()) } returns Unit

        mockMvc.post("/api/v1/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"some-token"}"""
        }.andExpect {
            status { isNoContent() }
        }
    }

    // ── POST /logout-all ──────────────────────────────────────────────────────

    @Test
    fun `POST logout-all - authenticated - returns 204`() {
        val userId = UUID.randomUUID()
        setAuth(userId)
        every { authService.logoutAll(userId) } returns Unit

        mockMvc.post("/api/v1/auth/logout-all").andExpect {
            status { isNoContent() }
        }

        verify { authService.logoutAll(userId) }
    }
}
