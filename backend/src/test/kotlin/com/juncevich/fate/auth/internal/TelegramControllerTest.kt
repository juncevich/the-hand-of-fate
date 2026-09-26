package com.juncevich.fate.auth.internal

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.GeneratedLinkToken
import com.juncevich.fate.auth.TelegramLinkService
import com.juncevich.fate.auth.internal.web.TelegramController
import com.juncevich.fate.shared.internal.config.ErrorHandler
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class TelegramControllerTest {
    private val telegramLinkService = mockk<TelegramLinkService>()
    private lateinit var mockMvc: MockMvc

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(TelegramController(telegramLinkService))
                .setControllerAdvice(ErrorHandler())
                .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
                .build()

        val principal = AuthenticatedUser(userId, "user@test.com")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `GET link-token - returns generated token`() {
        every { telegramLinkService.generateLinkToken(userId) } returns
            GeneratedLinkToken(token = "abc123", expiresAt = Instant.parse("2026-04-25T00:05:00Z"))

        mockMvc.get("/api/v1/telegram/link-token").andExpect {
            status { isOk() }
            jsonPath("$.token") { value("abc123") }
            jsonPath("$.expiresAt") { exists() }
        }
    }

    @Test
    fun `DELETE unlink - returns 204 and unlinks current user`() {
        every { telegramLinkService.unlinkByUserId(userId) } just runs

        mockMvc.delete("/api/v1/telegram/unlink").andExpect {
            status { isNoContent() }
        }

        verify { telegramLinkService.unlinkByUserId(userId) }
    }

    @Test
    fun `DELETE unlink - returns 409 when telegram is not linked`() {
        every { telegramLinkService.unlinkByUserId(userId) } throws IllegalStateException("Telegram is not linked")

        mockMvc.delete("/api/v1/telegram/unlink").andExpect {
            status { isConflict() }
        }
    }
}
