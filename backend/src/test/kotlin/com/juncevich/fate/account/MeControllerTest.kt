package com.juncevich.fate.account

import com.juncevich.fate.account.web.MeController
import com.juncevich.fate.notification.EmailPreferences
import com.juncevich.fate.platform.ErrorHandler
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class MeControllerTest {
    private val preferences = mockk<EmailPreferences>()
    private val users = mockk<UserQueryService>()
    private val userId = UUID.randomUUID()
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(MeController(preferences, users))
            .setControllerAdvice(ErrorHandler())
            .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
            .build()

    @BeforeEach
    fun signIn() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(AuthenticatedUser(userId, "ann@test.com"), null, emptyList())
    }

    @AfterEach
    fun signOut() = SecurityContextHolder.clearContext()

    @Test
    fun `reads and toggles notification emails of the caller's address`() {
        every { preferences.receivesNotifications("ann@test.com") } returns false
        every { preferences.setReceivesNotifications("ann@test.com", false) } just runs

        mockMvc.get("/api/v1/users/me/email-preferences").andExpect {
            status { isOk() }
            jsonPath("$.notificationsEnabled") { value(false) }
        }
        mockMvc
            .put("/api/v1/users/me/email-preferences") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"notificationsEnabled":false}"""
            }.andExpect { jsonPath("$.notificationsEnabled") { value(false) } }

        verify { preferences.setReceivesNotifications("ann@test.com", false) }
    }

    @Test
    fun `returns the caller's profile with a versioned avatar url`() {
        val avatarAt = Instant.ofEpochMilli(1_700_000_000_000)
        every { users.findById(userId) } returns
            User(id = userId, email = "ann@test.com", displayName = "Ann", avatarUpdatedAt = avatarAt)

        mockMvc.get("/api/v1/users/me").andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(userId.toString()) }
            jsonPath("$.displayName") { value("Ann") }
            jsonPath("$.avatarUrl") { value("/api/v1/users/$userId/avatar?v=1700000000000") }
        }
    }

    @Test
    fun `an account that is gone is 404`() {
        every { users.findById(userId) } returns null

        mockMvc.get("/api/v1/users/me").andExpect { status { isNotFound() } }
    }
}
