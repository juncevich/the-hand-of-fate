package com.juncevich.fate.vote.persistence.mapper

import com.juncevich.fate.account.UserProfile
import com.juncevich.fate.vote.domain.DrawAudit
import com.juncevich.fate.vote.domain.DrawHistory
import com.juncevich.fate.vote.domain.Vote
import com.juncevich.fate.vote.domain.VoteOption
import com.juncevich.fate.vote.domain.VoteParticipant
import com.juncevich.fate.vote.persistence.entity.DrawHistoryJpaEntity
import com.juncevich.fate.vote.persistence.entity.VoteJpaEntity
import com.juncevich.fate.vote.persistence.entity.VoteOptionJpaEntity
import com.juncevich.fate.vote.persistence.entity.VoteParticipantJpaEntity

fun VoteJpaEntity.toDomain(creator: UserProfile) =
    Vote(
        id = id,
        title = title,
        description = description,
        creator = creator,
        mode = mode,
        drawTarget = drawTarget,
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
        drawTarget = drawTarget,
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
    val audit = audit?.let { DrawAudit(it.seed, it.candidates) }
    // A participant win whose winner deleted their account keeps neither address nor option.
    return if (optionTitle != null) {
        DrawHistory.OptionWinner(
            id = id,
            voteId = vote.id,
            optionId = winnerOption?.id,
            optionTitle = optionTitle,
            round = round,
            drawnAt = drawnAt,
            audit = audit
        )
    } else {
        DrawHistory.ParticipantWinner(
            id = id,
            voteId = vote.id,
            email = email,
            displayName = winnerDisplayName,
            round = round,
            drawnAt = drawnAt,
            audit = audit
        )
    }
}
