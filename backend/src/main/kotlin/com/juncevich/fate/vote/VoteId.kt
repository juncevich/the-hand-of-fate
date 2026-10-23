package com.juncevich.fate.vote

import com.juncevich.fate.shared.uuidV7
import java.util.UUID

/** Identifies a vote; typed so a vote id and a user id can't be swapped in a call. */
@JvmInline
value class VoteId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun new() = VoteId(uuidV7())
    }
}
