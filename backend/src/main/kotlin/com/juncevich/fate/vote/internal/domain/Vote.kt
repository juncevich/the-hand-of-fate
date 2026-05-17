package com.juncevich.fate.vote.internal.domain

import com.juncevich.fate.auth.User
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import java.time.Instant
import java.util.UUID

class Vote(
    val id: UUID = UUID.randomUUID(),
    var title: String,
    var description: String? = null,
    val creator: User,
    var mode: VoteMode = VoteMode.SIMPLE,
    var status: VoteStatus = VoteStatus.PENDING,
    var currentRound: Int = 1,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    val version: Int = 0,
)
