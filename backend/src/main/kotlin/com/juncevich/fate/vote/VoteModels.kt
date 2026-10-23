package com.juncevich.fate.vote

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

// enumAsRef: one shared schema in api/openapi.yaml instead of a copy inlined into every DTO
@Schema(enumAsRef = true)
enum class VoteMode { SIMPLE, FAIR_ROTATION }

@Schema(enumAsRef = true)
enum class VoteStatus { PENDING, DRAWN, CLOSED }

/**
 * What a draw picks the winner from; fixed when the vote is created. With [OPTIONS] the
 * participants are observers: they see the vote and get its notifications but are never drawn.
 */
@Schema(
    enumAsRef = true,
    description =
        "What a draw picks the winner from; fixed when the vote is created. " +
            "With OPTIONS the participants are observers and are never drawn."
)
enum class DrawTarget { PARTICIPANTS, OPTIONS }

data class CreateVoteCommand(
    val title: String,
    val description: String? = null,
    val mode: VoteMode = VoteMode.SIMPLE,
    val participantEmails: List<String> = emptyList(),
    val options: List<String>? = null,
    /** `null` keeps older clients working: options given → [DrawTarget.OPTIONS], else participants. */
    val drawTarget: DrawTarget? = null,
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
    val drawTarget: DrawTarget,
    val status: VoteStatus,
    val currentRound: Int,
    val participants: List<ParticipantView>,
    val options: List<VoteOptionView>,
    val lastResult: DrawHistoryView?,
    val isCreator: Boolean,
    val createdAt: Instant,
)
