package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.User
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.storage.ObjectStorage
import com.juncevich.fate.storage.StorageUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/** Avatar bytes plus the version they were stored under (used as the HTTP ETag). */
class Avatar(
    val content: ByteArray,
    val contentType: String,
    val version: Long,
)

/** Relative URL of the user's avatar, versioned so a replaced avatar is never served from cache. */
fun User.avatarUrl(): String? = avatarUpdatedAt?.let { "/api/v1/users/$id/avatar?v=${it.toEpochMilli()}" }

/**
 * Each user has a single object under a fixed key, so replacing an avatar overwrites it and
 * never leaves an orphan behind. Storage I/O runs outside any DB transaction; the object is
 * written before the user row points at it, so the row never references a missing image.
 */
@Service
class AvatarService(
    private val userRepositoryPort: UserRepositoryPort,
    private val objectStorage: ObjectStorage,
    private val imageProcessor: AvatarImageProcessor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Stores [upload] as the user's avatar and returns its new URL. */
    fun upload(
        userId: UUID,
        upload: ByteArray,
    ): String {
        if (!objectStorage.isEnabled) throw StorageUnavailableException()
        val user = findUser(userId)
        objectStorage.put(avatarKey(userId), imageProcessor.toAvatarPng(upload), PNG)
        user.avatarUpdatedAt = Instant.now()
        return checkNotNull(userRepositoryPort.save(user).avatarUrl())
    }

    fun delete(userId: UUID) {
        if (!objectStorage.isEnabled) throw StorageUnavailableException()
        val user = findUser(userId)
        if (user.avatarUpdatedAt == null) return
        user.avatarUpdatedAt = null
        userRepositoryPort.save(user)
        // The row no longer references the object; a failed delete only leaves an unreachable
        // file that the next upload overwrites, so it must not fail the request.
        runCatching { objectStorage.delete(avatarKey(userId)) }
            .onFailure { log.warn("Could not delete avatar object of user {}: {}", userId, it.message) }
    }

    /** Best effort, for a deleted account: a leftover object is unreachable and harmless. */
    fun deleteStoredObject(userId: UUID) {
        if (!objectStorage.isEnabled) return
        runCatching { objectStorage.delete(avatarKey(userId)) }
            .onFailure { log.warn("Could not delete avatar object of deleted user {}: {}", userId, it.message) }
    }

    fun load(userId: UUID): Avatar {
        val updatedAt = findUser(userId).avatarUpdatedAt
        if (updatedAt == null || !objectStorage.isEnabled) throw NotFoundException("Avatar not found")
        val stored = objectStorage.get(avatarKey(userId)) ?: throw NotFoundException("Avatar not found")
        return Avatar(stored.content, stored.contentType, updatedAt.toEpochMilli())
    }

    private fun findUser(userId: UUID): User =
        userRepositoryPort.findById(userId) ?: throw NotFoundException("User not found")

    private fun avatarKey(userId: UUID) = "avatars/$userId.png"

    private companion object {
        const val PNG = "image/png"
    }
}
