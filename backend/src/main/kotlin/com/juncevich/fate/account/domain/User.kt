package com.juncevich.fate.account.domain

import com.juncevich.fate.shared.uuidV7
import java.time.Instant
import java.util.UUID

/**
 * An account of this application; who it is comes from Keycloak (docs/adr/0001). Immutable: a
 * change is a copy, stored with `UserRepository.update`. Private to the account module — other
 * modules see an account as `UserProfile`.
 */
data class User(
    val id: UUID = uuidV7(),
    val email: String,
    val displayName: String,
    val telegramId: Long? = null,
    val telegramName: String? = null,
    /** When the avatar was last replaced; `null` when the user has none. */
    val avatarUpdatedAt: Instant? = null,
    val createdAt: Instant,
) {
    val isTelegramLinked: Boolean get() = telegramId != null

    fun linkTelegram(
        telegramId: Long,
        telegramName: String,
    ) = copy(telegramId = telegramId, telegramName = telegramName)

    fun unlinkTelegram() = copy(telegramId = null, telegramName = null)

    /** [replacedAt] is when the avatar was replaced; `null` removes it. */
    fun withAvatar(replacedAt: Instant?) = copy(avatarUpdatedAt = replacedAt)
}
