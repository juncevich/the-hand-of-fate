package com.juncevich.fate.vote.internal.persistence.mapper

import com.juncevich.fate.auth.User
import com.juncevich.fate.vote.internal.domain.DrawHistory
import com.juncevich.fate.vote.internal.domain.Vote
import com.juncevich.fate.vote.internal.domain.VoteOption
import com.juncevich.fate.vote.internal.domain.VoteParticipant
import com.juncevich.fate.vote.internal.persistence.entity.DrawHistoryJpaEntity
import com.juncevich.fate.vote.internal.persistence.entity.VoteJpaEntity
import com.juncevich.fate.vote.internal.persistence.entity.VoteOptionJpaEntity
import com.juncevich.fate.vote.internal.persistence.entity.VoteParticipantJpaEntity

fun VoteJpaEntity.toDomain(creator: User) = Vote(
    id = id,
    title = title,
    description = description,
    creator = creator,
    mode = mode,
    status = status,
    currentRound = currentRound,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Vote.toJpaEntity() = VoteJpaEntity(
    id = id,
    title = title,
    description = description,
    creatorId = creator.id,
    mode = mode,
    status = status,
    currentRound = currentRound,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun VoteParticipantJpaEntity.toDomain() = VoteParticipant(
    id = id,
    voteId = vote.id,
    email = email,
    displayName = displayName,
    addedAt = addedAt,
)

fun VoteParticipant.toJpaEntity(voteEntity: VoteJpaEntity) = VoteParticipantJpaEntity(
    id = id,
    vote = voteEntity,
    email = email,
    displayName = displayName,
    addedAt = addedAt,
)

fun VoteOptionJpaEntity.toDomain() = VoteOption(
    id = id,
    voteId = vote.id,
    title = title,
    position = position,
    createdAt = createdAt,
)

fun VoteOption.toJpaEntity(voteEntity: VoteJpaEntity) = VoteOptionJpaEntity(
    id = id,
    vote = voteEntity,
    title = title,
    position = position,
    createdAt = createdAt,
)

fun DrawHistoryJpaEntity.toDomain() = DrawHistory(
    id = id,
    voteId = vote.id,
    winnerEmail = winnerEmail,
    winnerDisplayName = winnerDisplayName,
    winnerOptionId = winnerOption?.id,
    winnerOptionTitle = winnerOptionTitle,
    round = round,
    drawnAt = drawnAt,
)
