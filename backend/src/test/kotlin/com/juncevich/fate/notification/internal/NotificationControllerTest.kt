package com.juncevich.fate.notification.internal

import com.juncevich.fate.notification.EmailPreferences
import com.juncevich.fate.notification.internal.web.NotificationController
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.internal.config.ErrorHandler
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NotificationControllerTest {
    private val preferences = mockk<EmailPreferences>()
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(NotificationController(preferences))
            .setControllerAdvice(ErrorHandler())
            .build()

    @Test
    fun `the unsubscribe page posts the address and its signature`() {
        every { preferences.unsubscribe("a@test.com", "sig") } just runs

        mockMvc
            .post("/api/v1/notifications/unsubscribe") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"a@test.com","signature":"sig"}"""
            }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `mail clients unsubscribe in one click with the form body of RFC 8058`() {
        every { preferences.unsubscribe("a@test.com", "sig") } just runs

        mockMvc
            .post("/api/v1/notifications/unsubscribe/one-click?email=a@test.com&sig=sig") {
                contentType = MediaType.APPLICATION_FORM_URLENCODED
                content = "List-Unsubscribe=One-Click"
            }.andExpect { status { isNoContent() } }

        verify { preferences.unsubscribe("a@test.com", "sig") }
    }

    @Test
    fun `a bad signature is a 400 with its code`() {
        every { preferences.unsubscribe(any(), any()) } throws BadRequestException("invalid", code = "INVALID_TOKEN")

        mockMvc
            .post("/api/v1/notifications/unsubscribe") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"a@test.com","signature":"bad"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
    }
}
