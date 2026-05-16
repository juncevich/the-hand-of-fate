package com.juncevich.fate.vote.internal

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddParticipantRequest(
    @field:Email @field:NotBlank val email: String,
)

data class AddOptionRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
)

data class DrawResultResponse(
    val winnerEmail: String?,
    val winnerDisplayName: String?,
    val winnerOptionTitle: String?,
    val round: Int,
    val newRoundStarted: Boolean,
)
