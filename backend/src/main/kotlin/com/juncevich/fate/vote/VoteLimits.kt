package com.juncevich.fate.vote

/**
 * Size limits for a vote. The REST DTOs validate them up front; [VoteService] enforces them too,
 * since it is also reached from gRPC and through the incremental add-participant/add-option calls.
 */
object VoteLimits {
    const val MAX_DESCRIPTION_LENGTH = 2000

    /** All participants of a vote, the creator included. */
    const val MAX_PARTICIPANTS = 100

    const val MAX_OPTIONS = 100
}
