package com.juncevich.fate.account

import com.juncevich.fate.account.domain.TelegramLinkTokenRepository
import com.juncevich.fate.account.telegram.ExpiredTokenPurger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ExpiredTokenPurgerTest {
    private val linkTokens = mockk<TelegramLinkTokenRepository>()
    private val purger = ExpiredTokenPurger(linkTokens)

    @Test
    fun `purges expired link tokens`() {
        every { linkTokens.deleteExpired(any()) } returns 3

        purger.purge()

        verify { linkTokens.deleteExpired(any()) }
    }

    @Test
    fun `stays quiet when nothing expired`() {
        every { linkTokens.deleteExpired(any()) } returns 0

        purger.purge()

        verify(exactly = 1) { linkTokens.deleteExpired(any()) }
    }
}
