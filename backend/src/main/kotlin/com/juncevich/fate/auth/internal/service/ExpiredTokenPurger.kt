package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.internal.port.TelegramLinkTokenRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Expired Telegram link tokens are otherwise deleted only when someone presents them, so unused ones would pile up forever. */
@Component
class ExpiredTokenPurger(
    private val linkTokens: TelegramLinkTokenRepositoryPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.auth.token-purge-delay-ms:3600000}")
    @Transactional
    fun purge() {
        val link = linkTokens.deleteExpired(Instant.now())
        if (link > 0) {
            log.info("Purged {} expired Telegram link tokens", link)
        }
    }
}
