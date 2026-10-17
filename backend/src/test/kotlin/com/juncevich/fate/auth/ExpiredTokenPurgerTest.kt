package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.port.RefreshTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.TelegramLinkTokenRepositoryPort
import com.juncevich.fate.auth.internal.service.ExpiredTokenPurger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ExpiredTokenPurgerTest {
    private val refreshTokens = mockk<RefreshTokenRepositoryPort>()
    private val linkTokens = mockk<TelegramLinkTokenRepositoryPort>()
    private val purger = ExpiredTokenPurger(refreshTokens, linkTokens)

    @Test
    fun `purges expired refresh and link tokens`() {
        every { refreshTokens.deleteExpired(any()) } returns 3
        every { linkTokens.deleteExpired(any()) } returns 0

        purger.purge()

        verify { refreshTokens.deleteExpired(any()) }
        verify { linkTokens.deleteExpired(any()) }
    }

    @Test
    fun `stays quiet when nothing expired`() {
        every { refreshTokens.deleteExpired(any()) } returns 0
        every { linkTokens.deleteExpired(any()) } returns 0

        purger.purge()

        verify(exactly = 1) { refreshTokens.deleteExpired(any()) }
    }
}
