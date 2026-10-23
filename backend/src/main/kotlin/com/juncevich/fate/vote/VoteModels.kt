package com.juncevich.fate.vote

import java.time.Instant
import java.util.UUID

// No OpenAPI annotations here: every enum becomes one shared schema (`OpenApiConfig`), and the
// docs of these schemas are added by the web adapter (`vote/web/VoteOpenApiDocs`).

enum class VoteMode { SIMPLE, FAIR_ROTATION }

enum class VoteStatus { PENDING, DRAWN, CLOSED }

/**
 * What a draw picks the winner from; fixed when the vote is created. With [OPTIONS] the
 * participants are observers: they see the vote and get its notifications but are never drawn.
 */
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
// `vote/web`, protobuf in `grpc`), so they carry no serialization annotations.

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
    val winner: Winner,
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
