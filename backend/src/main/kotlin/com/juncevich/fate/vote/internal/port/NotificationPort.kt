package com.juncevich.fate.vote.internal.port

import com.juncevich.fate.vote.DrawResult
import com.juncevich.fate.vote.internal.domain.Vote

interface NotificationPort {
    fun notifyVoteInvitation(
        recipientEmail: String,
        vote: Vote,
    )

    fun notifyDrawResult(
        vote: Vote,
        result: DrawResult,
        participantEmails: List<String>,
    )
}
