package com.juncevich.fate.vote

/** Who a draw picked: one of the vote's participants or one of its options, by its `drawTarget`. */
sealed interface Winner {
    /** What to call the winner: the option title, else the participant's name, else their address. */
    val label: String

    data class Participant(
        /** `null` once the winner deleted their account (their name is then "Deleted user"). */
        val email: String?,
        val displayName: String?,
    ) : Winner {
        override val label: String get() = displayName ?: email ?: "Unknown"
    }

    data class Option(
        /** Kept even after the option is deleted. */
        val title: String,
    ) : Winner {
        override val label: String get() = title
    }
}

data class DrawResult(
    val winner: Winner,
    val round: Int,
    val newRoundStarted: Boolean,
)
