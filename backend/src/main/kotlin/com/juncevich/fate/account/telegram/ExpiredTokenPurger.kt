package com.juncevich.fate.account.telegram

import com.juncevich.fate.account.domain.TelegramLinkTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Expired Telegram link tokens are otherwise deleted only when someone presents them, so unused ones would pile up forever. */
@Component
class ExpiredTokenPurger(
    private val linkTokens: TelegramLinkTokenRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.auth.token-purge-delay-ms:3600000}")
    @Transactional
    fun purge() {
        val link = linkTokens.deleteExpired(clock.instant())
        if (link > 0) {
            log.info("Purged {} expired Telegram link tokens", link)
        }
    }
}
