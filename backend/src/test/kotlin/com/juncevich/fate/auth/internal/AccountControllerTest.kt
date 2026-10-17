package com.juncevich.fate.auth.internal

import com.juncevich.fate.auth.internal.service.AccountService
import com.juncevich.fate.auth.internal.web.AccountController
import com.juncevich.fate.shared.internal.config.ErrorHandler
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AccountControllerTest {
    private val accountService = mockk<AccountService>()
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(AccountController(accountService))
            .setControllerAdvice(ErrorHandler())
            .build()

    private fun post(
        path: String,
        json: String,
    ) = mockMvc.post("/api/v1/auth/$path") {
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    @Test
    fun `resend-verification and forgot-password always answer 202`() {
        every { accountService.resendVerification("a@test.com") } just Runs
        every { accountService.requestPasswordReset("a@test.com") } just Runs

        post("resend-verification", """{"email":"a@test.com"}""").andExpect { status { isAccepted() } }
        post("forgot-password", """{"email":"a@test.com"}""").andExpect { status { isAccepted() } }

        verify { accountService.resendVerification("a@test.com") }
        verify { accountService.requestPasswordReset("a@test.com") }
    }

    @Test
    fun `an invalid email is rejected before the service`() {
        post("forgot-password", """{"email":"nope"}""").andExpect { status { isBadRequest() } }
        verify(exactly = 0) { accountService.requestPasswordReset(any()) }
    }

    @Test
    fun `reset-password answers 204 and validates the new password`() {
        every { accountService.resetPassword("tok", "new-password") } just Runs

        post("reset-password", """{"token":"tok","newPassword":"new-password"}""").andExpect { status { isNoContent() } }
        post("reset-password", """{"token":"tok","newPassword":"short"}""").andExpect { status { isBadRequest() } }

        verify(exactly = 1) { accountService.resetPassword(any(), any()) }
    }
}
