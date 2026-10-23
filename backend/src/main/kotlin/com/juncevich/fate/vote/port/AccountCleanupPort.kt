package com.juncevich.fate.vote.port

interface AccountCleanupPort {
    /** Removes [email] from the participants of every vote. */
    fun removeParticipant(email: String)

    /** Keeps [email]'s past wins in the history, under [displayName] and without the address. */
    fun anonymizeWins(
        email: String,
        displayName: String,
    )
}
