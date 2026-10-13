package com.juncevich.fate.storage.internal

import com.juncevich.fate.storage.ObjectStorage
import com.juncevich.fate.storage.StoredObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

class S3ObjectStorage(
    private val s3: S3Client,
    private val properties: StorageProperties,
) : ObjectStorage,
    InitializingBean {
    private val log = LoggerFactory.getLogger(javaClass)

    override val isEnabled: Boolean = true

    override fun afterPropertiesSet() {
        if (properties.createBucket) ensureBucket()
    }

    override fun put(
        key: String,
        content: ByteArray,
        contentType: String,
    ) {
        try {
            upload(key, content, contentType)
        } catch (e: NoSuchBucketException) {
            // The local store may have come up after the backend; create the bucket lazily then.
            if (!properties.createBucket) throw e
            ensureBucket()
            upload(key, content, contentType)
        }
    }

    private fun upload(
        key: String,
        content: ByteArray,
        contentType: String,
    ) {
        s3.putObject({ it.bucket(properties.bucket).key(key).contentType(contentType) }, RequestBody.fromBytes(content))
    }

    override fun get(key: String): StoredObject? =
        try {
            val response = s3.getObjectAsBytes { it.bucket(properties.bucket).key(key) }
            StoredObject(response.asByteArray(), response.response().contentType())
        } catch (_: NoSuchKeyException) {
            null
        }

    override fun delete(key: String) {
        s3.deleteObject { it.bucket(properties.bucket).key(key) }
    }

    /** Dev convenience: a missing or unreachable store must not stop the rest of the app from booting. */
    private fun ensureBucket() {
        try {
            try {
                s3.headBucket { it.bucket(properties.bucket) }
            } catch (_: NoSuchBucketException) {
                s3.createBucket { it.bucket(properties.bucket) }
                log.info("Created storage bucket '{}'", properties.bucket)
            }
        } catch (e: SdkException) {
            log.warn(
                "Could not verify storage bucket '{}' at {}: {}",
                properties.bucket,
                properties.endpoint,
                e.message
            )
        }
    }
}
