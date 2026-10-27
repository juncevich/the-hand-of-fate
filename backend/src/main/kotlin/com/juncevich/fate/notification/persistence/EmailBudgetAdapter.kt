package com.juncevich.fate.notification.persistence

import com.juncevich.fate.notification.port.EmailBudgetPort
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.shared.uuidV7
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class EmailBudgetAdapter(
    private val jdbc: JdbcClient,
) : EmailBudgetPort {
    // Transaction-scoped advisory lock: concurrent actions of one user can't both pass the check.
    override fun lock(userId: UserId) {
        jdbc
            .sql("SELECT pg_advisory_xact_lock(:key)")
            .param("key", userId.value.mostSignificantBits xor userId.value.leastSignificantBits)
            .query()
            .singleRow()
    }

    override fun usedWithin(
        userId: UserId,
        window: Duration,
    ): Int =
        jdbc
            .sql(
                """
                SELECT COALESCE(SUM(recipients), 0) FROM email_budget_ledger
                WHERE user_id = :userId AND created_at > now() - make_interval(secs => :window)
                """
            ).param("userId", userId.value)
            .param("window", window.seconds())
            .query(Int::class.java)
            .single()

    override fun record(
        userId: UserId,
        recipients: Int,
    ) {
        jdbc
            .sql("INSERT INTO email_budget_ledger (id, user_id, recipients) VALUES (:id, :userId, :recipients)")
            .param("id", uuidV7())
            .param("userId", userId.value)
            .param("recipients", recipients)
            .update()
    }

    override fun purgeOlderThan(window: Duration): Int =
        jdbc
            .sql("DELETE FROM email_budget_ledger WHERE created_at <= now() - make_interval(secs => :window)")
            .param("window", window.seconds())
            .update()
}
