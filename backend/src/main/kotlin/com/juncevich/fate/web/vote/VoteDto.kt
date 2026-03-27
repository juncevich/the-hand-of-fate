package com.juncevich.fate.web.vote

import com.juncevich.fate.domain.vote.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateVoteRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val description: String? = null,
    val mode: VoteMode = VoteMode.SIMPLE,
    val participantEmails: List<@Email String> = emptyList(),
)

data class AddParticipantRequest(
    @field:Email @field:NotBlank val email: String,
)

data class VoteSummaryDto(
    val id: UUID,
    val title: String,
    val mode: VoteMode,
    val status: VoteStatus,
    val currentRound: Int,
    val participantCount: Long,
    val isCreator: Boolean,
    val createdAt: Instant,
)

data class VoteDetailDto(
    val id: UUID,
    val title: String,
    val description: String?,
    val mode: VoteMode,
    val status: VoteStatus,
    val currentRound: Int,
    val participants: List<ParticipantDto>,
    val lastResult: DrawHistoryDto?,
    val isCreator: Boolean,
    val createdAt: Instant,
)

data class ParticipantDto(
    val email: String,
    val displayName: String?,
)

data class DrawHistoryDto(
    val id: UUID,
    val winnerEmail: String,
    val winnerDisplayName: String?,
    val round: Int,
    val drawnAt: Instant,
)

data class DrawResultResponse(
    val winnerEmail: String,
    val winnerDisplayName: String?,
    val round: Int,
    val newRoundStarted: Boolean,
)

// ── Mapping extensions ───────────────────────────────────────────────────────

fun Vote.toSummaryDto(participantCount: Long, isCreator: Boolean) = VoteSummaryDto(
    id = id,
    title = title,
    mode = mode,
    status = status,
    currentRound = currentRound,
    participantCount = participantCount,
    isCreator = isCreator,
    createdAt = createdAt,
)

fun Vote.toDetailDto(
    participants: List<VoteParticipant>,
    lastResult: DrawHistory?,
    requesterId: UUID? = null,
) = VoteDetailDto(
    id = id,
    title = title,
    description = description,
    mode = mode,
    status = status,
    currentRound = currentRound,
    participants = participants.map { ParticipantDto(it.email, it.displayName) },
    lastResult = lastResult?.toDto(),
    isCreator = requesterId != null && creator.id == requesterId,
    createdAt = createdAt,
)

fun DrawHistory.toDto() = DrawHistoryDto(
    id = id,
    winnerEmail = winnerEmail,
    winnerDisplayName = winnerDisplayName,
    round = round,
    drawnAt = drawnAt,
)
