package com.juncevich.fate.storage.internal

import com.juncevich.fate.storage.ObjectStorage
import com.juncevich.fate.storage.StorageUnavailableException
import com.juncevich.fate.storage.StoredObject

/** Stand-in used when `app.storage.enabled` is false: features backed by storage report themselves unavailable. */
object DisabledObjectStorage : ObjectStorage {
    override val isEnabled: Boolean = false

    override fun put(
        key: String,
        content: ByteArray,
        contentType: String,
    ): Unit = throw StorageUnavailableException()

    override fun get(key: String): StoredObject = throw StorageUnavailableException()

    override fun delete(key: String): Unit = throw StorageUnavailableException()
}
