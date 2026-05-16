package com.juncevich.fate.auth.internal.persistence.mapper

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.domain.RefreshToken
import com.juncevich.fate.auth.internal.domain.TelegramLinkToken
import com.juncevich.fate.auth.internal.persistence.entity.RefreshTokenJpaEntity
import com.juncevich.fate.auth.internal.persistence.entity.TelegramLinkTokenJpaEntity
import com.juncevich.fate.auth.internal.persistence.entity.UserJpaEntity

fun UserJpaEntity.toDomain() = User(
    id = id,
    email = email,
    passwordHash = passwordHash,
    displayName = displayName,
    telegramId = telegramId,
    telegramName = telegramName,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun User.toJpaEntity() = UserJpaEntity(
    id = id,
    email = email,
    passwordHash = passwordHash,
    displayName = displayName,
    telegramId = telegramId,
    telegramName = telegramName,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RefreshTokenJpaEntity.toDomain() = RefreshToken(
    id = id,
    user = user.toDomain(),
    tokenHash = tokenHash,
    expiresAt = expiresAt,
    createdAt = createdAt,
)

fun RefreshToken.toJpaEntity(userEntity: UserJpaEntity) = RefreshTokenJpaEntity(
    id = id,
    user = userEntity,
    tokenHash = tokenHash,
    expiresAt = expiresAt,
    createdAt = createdAt,
)

fun TelegramLinkTokenJpaEntity.toDomain() = TelegramLinkToken(
    id = id,
    user = user.toDomain(),
    token = token,
    expiresAt = expiresAt,
    createdAt = createdAt,
)

fun TelegramLinkToken.toJpaEntity(userEntity: UserJpaEntity) = TelegramLinkTokenJpaEntity(
    id = id,
    user = userEntity,
    token = token,
    expiresAt = expiresAt,
    createdAt = createdAt,
)
