package com.juncevich.fate.auth.internal.persistence.mapper

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.domain.TelegramLinkToken
import com.juncevich.fate.auth.internal.persistence.entity.TelegramLinkTokenJpaEntity
import com.juncevich.fate.auth.internal.persistence.entity.UserJpaEntity

fun UserJpaEntity.toDomain() =
    User(
        id = id,
        email = email,
        displayName = displayName,
        telegramId = telegramId,
        telegramName = telegramName,
        avatarUpdatedAt = avatarUpdatedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

fun User.toJpaEntity() =
    UserJpaEntity(
        id = id,
        email = email,
        displayName = displayName,
        telegramId = telegramId,
        telegramName = telegramName,
        avatarUpdatedAt = avatarUpdatedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

fun TelegramLinkTokenJpaEntity.toDomain() =
    TelegramLinkToken(
        id = id,
        user = user.toDomain(),
        token = token,
        expiresAt = expiresAt,
        createdAt = createdAt
    )

fun TelegramLinkToken.toJpaEntity(userEntity: UserJpaEntity) =
    TelegramLinkTokenJpaEntity(
        id = id,
        user = userEntity,
        token = token,
        expiresAt = expiresAt,
        createdAt = createdAt
    )
