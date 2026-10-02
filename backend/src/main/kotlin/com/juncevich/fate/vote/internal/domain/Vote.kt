package com.juncevich.fate.vote.internal.domain

import com.juncevich.fate.auth.UserProfile
import com.juncevich.fate.shared.uuidV7
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import java.time.Instant
import java.util.UUID

class Vote(
    val id: UUID = uuidV7(),
    var title: String,
    var description: String? = null,
    val creator: UserProfile,
    var mode: VoteMode = VoteMode.SIMPLE,
    var status: VoteStatus = VoteStatus.PENDING,
    var currentRound: Int = 1,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    val version: Int = 0,
)
