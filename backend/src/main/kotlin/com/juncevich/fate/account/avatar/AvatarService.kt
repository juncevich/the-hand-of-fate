package com.juncevich.fate.account.avatar

import com.juncevich.fate.account.domain.User
import com.juncevich.fate.account.domain.UserRepository
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.storage.ObjectStorage
import com.juncevich.fate.storage.StorageUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock

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
    private val userRepository: UserRepository,
    private val objectStorage: ObjectStorage,
    private val imageProcessor: AvatarImageProcessor,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Stores [upload] as the user's avatar and returns its new URL. */
    fun upload(
        userId: UserId,
        upload: ByteArray,
    ): String {
        if (!objectStorage.isEnabled) throw StorageUnavailableException()
        val user = findUser(userId)
        objectStorage.put(avatarKey(userId), imageProcessor.toAvatarPng(upload), PNG)
        val updated = user.withAvatar(clock.instant())
        userRepository.update(updated)
        return checkNotNull(updated.avatarUrl())
    }

    fun delete(userId: UserId) {
        if (!objectStorage.isEnabled) throw StorageUnavailableException()
        val user = findUser(userId)
        if (user.avatarUpdatedAt == null) return
        userRepository.update(user.withAvatar(null))
        // The row no longer references the object; a failed delete only leaves an unreachable
        // file that the next upload overwrites, so it must not fail the request.
        deleteObjectQuietly(userId, "user")
    }

    /** Best effort, for a deleted account: a leftover object is unreachable and harmless. */
    fun deleteStoredObject(userId: UserId) {
        if (!objectStorage.isEnabled) return
        deleteObjectQuietly(userId, "deleted user")
    }

    // Whatever the store answers (unreachable, denied, a bad response) is only logged; an Error is not caught.
    @Suppress("TooGenericExceptionCaught")
    private fun deleteObjectQuietly(
        userId: UserId,
        whose: String,
    ) {
        try {
            objectStorage.delete(avatarKey(userId))
        } catch (ex: Exception) {
            log.warn("Could not delete avatar object of {} {}: {}", whose, userId, ex.message)
        }
    }

    fun load(userId: UserId): Avatar {
        val updatedAt = findUser(userId).avatarUpdatedAt
        if (updatedAt == null ||
            !objectStorage.isEnabled
        ) {
            throw NotFoundException("Avatar not found", ErrorCode.AVATAR_NOT_FOUND)
        }
        val stored =
            objectStorage.get(avatarKey(userId))
                ?: throw NotFoundException("Avatar not found", ErrorCode.AVATAR_NOT_FOUND)
        return Avatar(stored.content, stored.contentType, updatedAt.toEpochMilli())
    }

    private fun findUser(userId: UserId): User =
        userRepository.findById(userId) ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)

    private fun avatarKey(userId: UserId) = "avatars/$userId.png"

    private companion object {
        const val PNG = "image/png"
    }
}
