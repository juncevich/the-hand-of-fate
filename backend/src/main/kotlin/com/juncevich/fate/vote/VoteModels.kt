package com.juncevich.fate.vote

import com.juncevich.fate.vote.internal.domain.DrawHistory
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.domain.VoteOption
import com.juncevich.fate.vote.internal.domain.VoteParticipant
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

// enumAsRef: one shared schema in api/openapi.yaml instead of a copy inlined into every DTO
@Schema(enumAsRef = true)
enum class VoteMode { SIMPLE, FAIR_ROTATION }

@Schema(enumAsRef = true)
enum class VoteStatus { PENDING, DRAWN, CLOSED }

data class CreateVoteCommand(
    val title: String,
    val description: String? = null,
    val mode: VoteMode = VoteMode.SIMPLE,
    val participantEmails: List<String> = emptyList(),
    val options: List<String>? = null,
)

// Read models of the module API. Adapters map them to their own wire format (REST DTOs in
// `internal/web`, protobuf in `grpc`), so they carry no serialization annotations.

data class VoteSummaryView(
    val id: UUID,
    val title: String,
    val mode: VoteMode,
    val status: VoteStatus,
    val currentRound: Int,
    val participantCount: Long,
    val isCreator: Boolean,
    val createdAt: Instant,
)

data class VoteOptionView(
    val id: UUID,
    val title: String,
)

data class ParticipantView(
    val email: String,
    val displayName: String?,
)

data class DrawHistoryView(
    val id: UUID,
    val winnerEmail: String?,
    val winnerDisplayName: String?,
    val winnerOptionTitle: String?,
    val round: Int,
    val drawnAt: Instant,
)

data class VoteDetailView(
    val id: UUID,
    val title: String,
    val description: String?,
    val mode: VoteMode,
    val status: VoteStatus,
    val currentRound: Int,
    val participants: List<ParticipantView>,
    val options: List<VoteOptionView>,
    val lastResult: DrawHistoryView?,
    val isCreator: Boolean,
    val createdAt: Instant,
)

// ── Domain → view mappings ──────────────────────────────────────────────────────

fun Vote.toSummaryView(
    participantCount: Long,
    isCreator: Boolean,
) = VoteSummaryView(
    id = id,
    title = title,
    mode = mode,
    status = status,
    currentRound = currentRound,
    participantCount = participantCount,
    isCreator = isCreator,
    createdAt = createdAt
)

fun Vote.toDetailView(
    participants: List<VoteParticipant>,
    options: List<VoteOption>,
    lastResult: DrawHistory?,
    requesterId: UUID? = null,
) = VoteDetailView(
    id = id,
    title = title,
    description = description,
    mode = mode,
    status = status,
    currentRound = currentRound,
    participants = participants.map { ParticipantView(it.email, it.displayName) },
    options = options.map { VoteOptionView(it.id, it.title) },
    lastResult = lastResult?.toView(),
    isCreator = requesterId != null && creator.id == requesterId,
    createdAt = createdAt
)

fun DrawHistory.toView() =
    when (this) {
        is DrawHistory.ParticipantWinner -> {
            DrawHistoryView(
                id = id,
                winnerEmail = email,
                winnerDisplayName = displayName,
                winnerOptionTitle = null,
                round = round,
                drawnAt = drawnAt
            )
        }

        is DrawHistory.OptionWinner -> {
            DrawHistoryView(
                id = id,
                winnerEmail = null,
                winnerDisplayName = null,
                winnerOptionTitle = optionTitle,
                round = round,
                drawnAt = drawnAt
            )
        }
    }
