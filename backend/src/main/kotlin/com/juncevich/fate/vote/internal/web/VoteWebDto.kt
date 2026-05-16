package com.juncevich.fate.vote.internal.web

import com.juncevich.fate.vote.CreateVoteCommand
import com.juncevich.fate.vote.VoteMode
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateVoteWebRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val description: String? = null,
    val mode: VoteMode = VoteMode.SIMPLE,
    val participantEmails: List<@Email String> = emptyList(),
    val options: List<String>? = null,
) {
    fun toCommand() =
        CreateVoteCommand(
            title = title,
            description = description,
            mode = mode,
            participantEmails = participantEmails,
            options = options
        )
}

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
