package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.internal.port.EmailTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.RefreshTokenRepositoryPort
import com.juncevich.fate.auth.internal.port.TelegramLinkTokenRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Expired refresh, Telegram link and email (verification/reset) tokens are otherwise deleted only when someone presents
 * them, so abandoned sessions and unused link tokens would pile up forever.
 */
@Component
class ExpiredTokenPurger(
    private val refreshTokens: RefreshTokenRepositoryPort,
    private val linkTokens: TelegramLinkTokenRepositoryPort,
    private val emailTokens: EmailTokenRepositoryPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.auth.token-purge-delay-ms:3600000}")
    @Transactional
    fun purge() {
        val now = Instant.now()
        val refresh = refreshTokens.deleteExpired(now)
        val link = linkTokens.deleteExpired(now)
        val email = emailTokens.deleteExpired(now)
        if (refresh + link + email > 0) {
            log.info("Purged expired tokens: {} refresh, {} Telegram link, {} email", refresh, link, email)
        }
    }
}
