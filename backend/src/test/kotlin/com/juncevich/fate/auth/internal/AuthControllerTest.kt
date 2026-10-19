package com.juncevich.fate.auth.internal

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.internal.service.AccountDeletionService
import com.juncevich.fate.auth.internal.service.AccountService
import com.juncevich.fate.auth.internal.service.AuthService
import com.juncevich.fate.auth.internal.service.AuthTokens
import com.juncevich.fate.auth.internal.service.LoginThrottledException
import com.juncevich.fate.auth.internal.service.RegistrationResult
import com.juncevich.fate.auth.internal.token.JwtProperties
import com.juncevich.fate.auth.internal.web.AuthController
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.internal.config.ErrorHandler
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
    private val accountService = mockk<AccountService>()
    private val accountDeletionService = mockk<AccountDeletionService>()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    AuthController(
                        authService,
                        accountService,
                        accountDeletionService,
                        JwtProperties("test-secret-that-is-definitely-long-enough-for-hmac-sha256")
                    )
                ).setControllerAdvice(ErrorHandler())
                .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
                .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun authTokens() =
        AuthTokens(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            userId = UUID.randomUUID(),
            email = "user@test.com",
            displayName = "User",
            avatarUrl = null
        )

    private fun setAuth(id: UUID = UUID.randomUUID()) {
        val principal = AuthenticatedUser(id, "user@test.com")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
    }

    @Test
    fun `POST register - valid body - returns 202 without a session`() {
        every { accountService.register("user@test.com", "password123", "User") } returns RegistrationResult("user@test.com")

        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"user@test.com","password":"password123","displayName":"User"}"""
            }.andExpect {
                status { isAccepted() }
                jsonPath("$.email") { value("user@test.com") }
                jsonPath("$.accessToken") { doesNotExist() }
                header { doesNotExist("Set-Cookie") }
            }
    }

    @Test
    fun `POST verify-email - signs in with a refresh cookie`() {
        every { accountService.verifyEmail("tok") } returns authTokens()

        mockMvc
            .post("/api/v1/auth/verify-email") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"tok"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.accessToken") { value("access-token") }
                header { string("Set-Cookie", org.hamcrest.Matchers.containsString("fate_refresh_token=refresh-token")) }
            }
    }

    @Test
    fun `POST change-password - returns the new session`() {
        val id = UUID.randomUUID()
        setAuth(id)
        every { accountService.changePassword(id, "old-password", "new-password") } returns authTokens()

        mockMvc
            .post("/api/v1/auth/change-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"currentPassword":"old-password","newPassword":"new-password"}"""
            }.andExpect {
                status { isOk() }
                header { string("Set-Cookie", org.hamcrest.Matchers.containsString("fate_refresh_token=refresh-token")) }
            }
    }

    @Test
    fun `POST change-password - a short new password - returns 400`() {
        setAuth()

        mockMvc
            .post("/api/v1/auth/change-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"currentPassword":"old-password","newPassword":"short"}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST delete-account - deletes and clears the refresh cookie`() {
        val id = UUID.randomUUID()
        setAuth(id)
        every { accountDeletionService.deleteAccount(id, "password123") } returns Unit

        mockMvc
            .post("/api/v1/auth/delete-account") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"password":"password123"}"""
            }.andExpect {
                status { isNoContent() }
                header { string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")) }
            }
    }

    @Test
    fun `a domain error carries its machine-readable code`() {
        every { accountService.verifyEmail("old") } throws
            com.juncevich.fate.shared
                .BadRequestException("This link has expired", ErrorCode.TOKEN_EXPIRED)

        mockMvc
            .post("/api/v1/auth/verify-email") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"old"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("TOKEN_EXPIRED") }
            }
    }

    @Test
    fun `POST register - invalid email - returns 400 with field errors`() {
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"not-an-email","password":"password123","displayName":"User"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.email") { exists() }
            }
    }

    @Test
    fun `POST register - short password - returns 400`() {
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"user@test.com","password":"short","displayName":"User"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.password") { exists() }
            }
    }

    @Test
    fun `POST register - blank display name - returns 400`() {
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"user@test.com","password":"password123","displayName":""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.displayName") { exists() }
            }
    }

    @Test
    fun `POST login - valid credentials - returns 200`() {
        every { authService.login(any(), any()) } returns authTokens()

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"user@test.com","password":"password123"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.accessToken") { value("access-token") }
                header { string("Set-Cookie", org.hamcrest.Matchers.containsString("fate_refresh_token=refresh-token")) }
            }
    }

    @Test
    fun `POST login - throttled account - returns 429 with Retry-After`() {
        every { authService.login(any(), any()) } throws LoginThrottledException(retryAfterSeconds = 120)

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"user@test.com","password":"password123"}"""
            }.andExpect {
                status { isTooManyRequests() }
                header { string("Retry-After", "120") }
                jsonPath("$.status") { value(429) }
                jsonPath("$.title") { value("Too many failed login attempts, please try again later") }
            }
    }

    @Test
    fun `POST login - blank password - returns 400`() {
        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"user@test.com","password":""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST refresh - valid token - returns 200`() {
        every { authService.refresh(any()) } returns authTokens()

        mockMvc
            .post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"some-token"}"""
            }.andExpect {
                status { isOk() }
                header { string("Set-Cookie", org.hamcrest.Matchers.containsString("fate_refresh_token=refresh-token")) }
            }
    }

    @Test
    fun `POST logout - valid token - returns 204`() {
        every { authService.logout(any()) } returns Unit

        mockMvc
            .post("/api/v1/auth/logout") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"some-token"}"""
            }.andExpect {
                status { isNoContent() }
            }
    }

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
