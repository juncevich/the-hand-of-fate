package com.juncevich.fate.vote

import com.juncevich.fate.vote.internal.domain.DrawHistory
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.domain.VoteOption
import com.juncevich.fate.vote.internal.domain.VoteParticipant
import java.time.Instant
import java.util.UUID

enum class VoteMode { SIMPLE, FAIR_ROTATION }

enum class VoteStatus { PENDING, DRAWN, CLOSED }

data class CreateVoteCommand(
    val title: String,
    val description: String? = null,
    val mode: VoteMode = VoteMode.SIMPLE,
    val participantEmails: List<String> = emptyList(),
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

// ── Domain → DTO mappings ──────────────────────────────────────────────────────

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
    options: List<VoteOption>,
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
    options = options.map { VoteOptionDto(it.id, it.title) },
    lastResult = lastResult?.toDto(),
    isCreator = requesterId != null && creator.id == requesterId,
    createdAt = createdAt,
)

fun DrawHistory.toDto() = DrawHistoryDto(
    id = id,
    winnerEmail = winnerEmail,
    winnerDisplayName = winnerDisplayName,
    winnerOptionTitle = winnerOptionTitle,
    round = round,
    drawnAt = drawnAt,
)
