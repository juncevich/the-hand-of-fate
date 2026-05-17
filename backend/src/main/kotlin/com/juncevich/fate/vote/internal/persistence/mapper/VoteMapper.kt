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

fun VoteJpaEntity.toDomain(creator: User) =
    Vote(
        id = id,
        title = title,
        description = description,
        creator = creator,
        mode = mode,
        status = status,
        currentRound = currentRound,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version
    )

fun Vote.toJpaEntity() =
    VoteJpaEntity(
        id = id,
        title = title,
        description = description,
        creatorId = creator.id,
        mode = mode,
        status = status,
        currentRound = currentRound,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version
    )

fun VoteParticipantJpaEntity.toDomain() =
    VoteParticipant(
        id = id,
        voteId = vote.id,
        email = email,
        displayName = displayName,
        addedAt = addedAt
    )

fun VoteParticipant.toJpaEntity(voteEntity: VoteJpaEntity) =
    VoteParticipantJpaEntity(
        id = id,
        vote = voteEntity,
        email = email,
        displayName = displayName,
        addedAt = addedAt
    )

fun VoteOptionJpaEntity.toDomain() =
    VoteOption(
        id = id,
        voteId = vote.id,
        title = title,
        position = position,
        createdAt = createdAt
    )

fun VoteOption.toJpaEntity(voteEntity: VoteJpaEntity) =
    VoteOptionJpaEntity(
        id = id,
        vote = voteEntity,
        title = title,
        position = position,
        createdAt = createdAt
    )

fun DrawHistoryJpaEntity.toDomain(): DrawHistory {
    val email = winnerEmail
    val optionTitle = winnerOptionTitle
    return when {
        email != null -> {
            DrawHistory.ParticipantWinner(
                id = id,
                voteId = vote.id,
                email = email,
                displayName = winnerDisplayName,
                round = round,
                drawnAt = drawnAt
            )
        }

        optionTitle != null -> {
            DrawHistory.OptionWinner(
                id = id,
                voteId = vote.id,
                optionId = checkNotNull(winnerOption?.id) { "winnerOption missing for OptionWinner history $id" },
                optionTitle = optionTitle,
                round = round,
                drawnAt = drawnAt
            )
        }

        else -> {
            error("DrawHistoryJpaEntity $id has neither winnerEmail nor winnerOptionTitle")
        }
    }
}
