package com.juncevich.fate.storage

import com.juncevich.fate.shared.ConflictException

/**
 * Binary object store (S3 API). Callers own the key layout; values are stored and returned as-is.
 * When storage is not configured (`app.storage.enabled=false`) [isEnabled] is false and every
 * operation throws [StorageUnavailableException].
 */
interface ObjectStorage {
    val isEnabled: Boolean

    fun put(
        key: String,
        content: ByteArray,
        contentType: String,
    )

    /** Returns `null` when no object exists under [key]. */
    fun get(key: String): StoredObject?

    /** Deleting a missing key is not an error. */
    fun delete(key: String)
}

class StoredObject(
    val content: ByteArray,
    val contentType: String,
)

class StorageUnavailableException : ConflictException("File storage is not configured")
