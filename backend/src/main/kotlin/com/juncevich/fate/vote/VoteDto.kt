package com.juncevich.fate.vote

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
    val options: List<String>? = null,
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
    val status: VoteStatus,
    val currentRound: Int,
    val participants: List<ParticipantDto>,
    val options: List<VoteOptionDto>,
    val lastResult: DrawHistoryDto?,
    val isCreator: Boolean,
    val createdAt: Instant,
)
