package com.juncevich.fate.vote

import java.util.UUID

fun Vote.toSummaryDto(
    participantCount: Long,
    isCreator: Boolean,
) = VoteSummaryDto(
    id = id,
    title = title,
    mode = mode,
    status = status,
    currentRound = currentRound,
    participantCount = participantCount,
    isCreator = isCreator,
    createdAt = createdAt
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
    createdAt = createdAt
)

fun DrawHistory.toDto() =
    DrawHistoryDto(
        id = id,
        winnerEmail = winnerEmail,
        winnerDisplayName = winnerDisplayName,
        winnerOptionTitle = winnerOptionTitle,
        round = round,
        drawnAt = drawnAt
    )
