package com.juncevich.fate.vote

data class DrawResult(
    val winnerEmail: String?,
    val winnerDisplayName: String?,
    val winnerOptionTitle: String?,
    val round: Int,
    val newRoundStarted: Boolean,
) {
    val winnerLabel: String get() = winnerOptionTitle ?: winnerDisplayName ?: winnerEmail ?: "Unknown"
}
