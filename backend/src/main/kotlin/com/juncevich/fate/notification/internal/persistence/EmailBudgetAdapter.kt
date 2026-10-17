package com.juncevich.fate.notification.internal.persistence

import com.juncevich.fate.notification.internal.port.EmailBudgetPort
import com.juncevich.fate.shared.uuidV7
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

private const val MILLIS_PER_SECOND = 1000.0

@Component
class EmailBudgetAdapter(
    private val jdbc: JdbcTemplate,
) : EmailBudgetPort {
    // Transaction-scoped advisory lock: concurrent actions of one user can't both pass the check.
    override fun lock(userId: UUID) {
        jdbc.query(
            "SELECT pg_advisory_xact_lock(?)",
            { _ -> },
            userId.mostSignificantBits xor userId.leastSignificantBits
        )
    }

    override fun usedWithin(
        userId: UUID,
        window: Duration,
    ): Int =
        jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(recipients), 0) FROM email_budget_ledger
            WHERE user_id = ? AND created_at > now() - make_interval(secs => ?)
            """,
            Int::class.java,
            userId,
            window.toMillis() / MILLIS_PER_SECOND
        ) ?: 0

    override fun record(
        userId: UUID,
        recipients: Int,
    ) {
        jdbc.update(
            "INSERT INTO email_budget_ledger (id, user_id, recipients) VALUES (?, ?, ?)",
            uuidV7(),
            userId,
            recipients
        )
    }

    override fun purgeOlderThan(window: Duration): Int =
        jdbc.update(
            "DELETE FROM email_budget_ledger WHERE created_at <= now() - make_interval(secs => ?)",
            window.toMillis() / MILLIS_PER_SECOND
        )
}
