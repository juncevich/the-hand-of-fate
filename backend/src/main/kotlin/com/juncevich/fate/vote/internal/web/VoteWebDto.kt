package com.juncevich.fate.vote.internal.web

import com.fasterxml.jackson.annotation.JsonProperty
import com.juncevich.fate.vote.CreateVoteCommand
import com.juncevich.fate.vote.DrawHistoryView
import com.juncevich.fate.vote.DrawResult
import com.juncevich.fate.vote.DrawTarget
import com.juncevich.fate.vote.VoteDetailView
import com.juncevich.fate.vote.VoteLimits
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import com.juncevich.fate.vote.VoteSummaryView
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// ── Requests ───────────────────────────────────────────────────────────────────

data class CreateVoteWebRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    @field:Size(max = VoteLimits.MAX_DESCRIPTION_LENGTH) val description: String? = null,
    val mode: VoteMode = VoteMode.SIMPLE,
    @field:Size(max = VoteLimits.MAX_PARTICIPANTS) val participantEmails: List<@Email String> = emptyList(),
    @field:Size(max = VoteLimits.MAX_OPTIONS) val options: List<String>? = null,
    val drawTarget: DrawTarget? = null,
) {
    fun toCommand() =
        CreateVoteCommand(
            title = title,
            description = description,
            mode = mode,
            participantEmails = participantEmails,
            options = options,
            drawTarget = drawTarget
        )
}

data class AddParticipantRequest(
    @field:Email @field:NotBlank val email: String,
)

data class AddOptionRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
)

data class ReorderOptionsRequest(
    @field:Size(max = VoteLimits.MAX_OPTIONS) val optionIds: List<UUID>,
)

// ── Responses ──────────────────────────────────────────────────────────────────
// Class names are the schema names in api/openapi.yaml (and in the generated clients).

data class VoteSummaryDto(
    val id: UUID,
    val title: String,
    val mode: VoteMode,
    val status: VoteStatus,
    val currentRound: Int,
    val participantCount: Long,
    // Without it Jackson derives the name `creator` from the Kotlin `is` getter.
    @get:JsonProperty("isCreator") val isCreator: Boolean,
    val createdAt: Instant,
)

data class VoteOptionDto(
    val id: UUID,
    val title: String,
)

data class ParticipantDto(
    val email: String,
    val displayName: String?,
)

data class DrawHistoryDto(
    val id: UUID,
    val winnerEmail: String?,
    val winnerDisplayName: String?,
    val winnerOptionTitle: String?,
    val round: Int,
    val drawnAt: Instant,
)

data class VoteDetailDto(
    val id: UUID,
    val title: String,
    val description: String?,
    val mode: VoteMode,
    val drawTarget: DrawTarget,
    val status: VoteStatus,
    val currentRound: Int,
    val participants: List<ParticipantDto>,
    val options: List<VoteOptionDto>,
    val lastResult: DrawHistoryDto?,
    @get:JsonProperty("isCreator") val isCreator: Boolean,
    val createdAt: Instant,
)

data class VoteStatsDto(
    val createdCount: Long,
)

data class DrawResultResponse(
    val winnerEmail: String?,
    val winnerDisplayName: String?,
    val winnerOptionTitle: String?,
    val round: Int,
    val newRoundStarted: Boolean,
)

// ── View → DTO mappings ────────────────────────────────────────────────────────

fun VoteSummaryView.toDto() =
    VoteSummaryDto(
        id = id,
        title = title,
        mode = mode,
        status = status,
        currentRound = currentRound,
        participantCount = participantCount,
        isCreator = isCreator,
        createdAt = createdAt
    )

fun VoteDetailView.toDto() =
    VoteDetailDto(
        id = id,
        title = title,
        description = description,
        mode = mode,
        drawTarget = drawTarget,
        status = status,
        currentRound = currentRound,
        participants = participants.map { ParticipantDto(it.email, it.displayName) },
        options = options.map { VoteOptionDto(it.id, it.title) },
        lastResult = lastResult?.toDto(),
        isCreator = isCreator,
        createdAt = createdAt
    )

fun DrawHistoryView.toDto() =
    DrawHistoryDto(
        id = id,
        winnerEmail = winnerEmail,
        winnerDisplayName = winnerDisplayName,
        winnerOptionTitle = winnerOptionTitle,
        round = round,
        drawnAt = drawnAt
    )

// Mapped field by field rather than returned as is: DrawResult.winnerLabel is a computed
// property for the bot's reply, and Jackson would serialize it into the REST response.
fun DrawResult.toResponse() =
    DrawResultResponse(
        winnerEmail = winnerEmail,
        winnerDisplayName = winnerDisplayName,
        winnerOptionTitle = winnerOptionTitle,
        round = round,
        newRoundStarted = newRoundStarted
    )
