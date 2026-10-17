package com.juncevich.fate.auth.internal

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.internal.web.MeController
import com.juncevich.fate.notification.EmailPreferences
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
import java.util.UUID

class MeControllerTest {
    private val preferences = mockk<EmailPreferences>()
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(MeController(preferences))
            .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
            .build()

    @BeforeEach
    fun signIn() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(AuthenticatedUser(UUID.randomUUID(), "ann@test.com"), null, emptyList())
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
}
