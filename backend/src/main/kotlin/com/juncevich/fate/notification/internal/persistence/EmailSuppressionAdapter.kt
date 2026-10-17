package com.juncevich.fate.notification.internal.persistence

import com.juncevich.fate.notification.internal.port.EmailSuppressionPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class EmailSuppressionAdapter(
    private val jdbc: JdbcTemplate,
) : EmailSuppressionPort {
    override fun isSuppressed(email: String): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM email_suppressions WHERE email = ?)",
            Boolean::class.java,
            email
        )
            ?: false

    override fun suppress(email: String) {
        jdbc.update("INSERT INTO email_suppressions (email) VALUES (?) ON CONFLICT DO NOTHING", email)
    }

    override fun allow(email: String) {
        jdbc.update("DELETE FROM email_suppressions WHERE email = ?", email)
    }
}
