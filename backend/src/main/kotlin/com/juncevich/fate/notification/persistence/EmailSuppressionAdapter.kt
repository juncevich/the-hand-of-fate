package com.juncevich.fate.notification.persistence

import com.juncevich.fate.notification.port.EmailSuppressionPort
import com.juncevich.fate.shared.Email
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

@Component
class EmailSuppressionAdapter(
    private val jdbc: JdbcClient,
) : EmailSuppressionPort {
    override fun isSuppressed(email: Email): Boolean =
        jdbc
            .sql("SELECT EXISTS (SELECT 1 FROM email_suppressions WHERE email = :email)")
            .param("email", email.value)
            .query(Boolean::class.java)
            .single()

    override fun suppress(email: Email) {
        jdbc
            .sql("INSERT INTO email_suppressions (email) VALUES (:email) ON CONFLICT DO NOTHING")
            .param("email", email.value)
            .update()
    }

    override fun allow(email: Email) {
        jdbc.sql("DELETE FROM email_suppressions WHERE email = :email").param("email", email.value).update()
    }
}
