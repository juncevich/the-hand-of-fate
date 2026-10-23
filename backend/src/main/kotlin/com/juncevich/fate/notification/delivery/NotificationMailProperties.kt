package com.juncevich.fate.notification.delivery

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.Locale

private const val DEFAULT_FROM = "noreply@handoffate.app"

/**
 * The notification emails (`app.mail`): sender, SMTP concurrency and language. Retries of a
 * single send are configured on `EmailService` (`app.mail.retry.delay-ms`).
 */
@ConfigurationProperties(prefix = "app.mail")
data class NotificationMailProperties(
    /** Sender address; blank falls back to the SMTP username, then to [DEFAULT_FROM]. */
    val from: String = "",
    /** Simultaneous SMTP sends, so the mail server doesn't throttle or reject us. */
    val maxConcurrentSends: Int = 4,
    /** Language of every notification email: a recipient may have no account to take one from. */
    val locale: Locale = Locale.forLanguageTag("ru"),
) {
    init {
        require(maxConcurrentSends > 0) { "app.mail.max-concurrent-sends must be positive" }
    }

    // An unset MAIL_USERNAME resolves to "" rather than "absent", so take the first non-blank one.
    fun sender(smtpUsername: String?): String =
        listOf(from, smtpUsername.orEmpty()).firstOrNull { it.isNotBlank() } ?: DEFAULT_FROM
}
