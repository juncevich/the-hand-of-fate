package com.juncevich.fate.vote.internal.persistence.adapter

import com.juncevich.fate.vote.internal.port.AccountCleanupPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class AccountCleanupAdapter(
    private val jdbc: JdbcTemplate,
) : AccountCleanupPort {
    override fun removeParticipant(email: String) {
        jdbc.update("DELETE FROM vote_participants WHERE email = ?", email)
    }

    override fun anonymizeWins(
        email: String,
        displayName: String,
    ) {
        jdbc.update(
            "UPDATE draw_history SET winner_email = NULL, winner_display_name = ? WHERE winner_email = ?",
            displayName,
            email
        )
    }
}
