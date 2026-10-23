package com.juncevich.fate.storage

import com.juncevich.fate.storage.s3.DisabledObjectStorage
import com.juncevich.fate.storage.s3.S3ObjectStorage
import com.juncevich.fate.storage.s3.StorageConfig
import com.juncevich.fate.storage.s3.StorageProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.CreateBucketResponse
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.util.function.Consumer

class StorageTest {
    private val s3 = mockk<S3Client>(relaxed = true)

    private fun storage(createBucket: Boolean = false) = S3ObjectStorage(s3, StorageProperties(enabled = true, createBucket = createBucket))

    private val noSuchBucket = NoSuchBucketException.builder().message("missing").build()

    @Test
    fun `put get and delete address the configured bucket`() {
        val response =
            ResponseBytes.fromByteArray(GetObjectResponse.builder().contentType("image/png").build(), byteArrayOf(1))
        every { s3.getObjectAsBytes(any<Consumer<GetObjectRequest.Builder>>()) } returns response

        val storage = storage()
        storage.put("k", byteArrayOf(1), "image/png")
        val stored = storage.get("k")
        storage.delete("k")

        assertTrue(storage.isEnabled)
        assertArrayEquals(byteArrayOf(1), stored?.content)
        assertEquals("image/png", stored?.contentType)
        verify { s3.putObject(any<Consumer<PutObjectRequest.Builder>>(), any<RequestBody>()) }
        verify { s3.deleteObject(any<Consumer<DeleteObjectRequest.Builder>>()) }
    }

    @Test
    fun `get returns null for a missing key`() {
        every { s3.getObjectAsBytes(any<Consumer<GetObjectRequest.Builder>>()) } throws
            NoSuchKeyException.builder().message("missing").build()

        assertNull(storage().get("k"))
    }

    @Test
    fun `startup creates a missing bucket only when asked to`() {
        every { s3.headBucket(any<Consumer<HeadBucketRequest.Builder>>()) } throws noSuchBucket
        every { s3.createBucket(any<Consumer<CreateBucketRequest.Builder>>()) } returns CreateBucketResponse.builder().build()

        storage(createBucket = false).afterPropertiesSet()
        verify(exactly = 0) { s3.headBucket(any<Consumer<HeadBucketRequest.Builder>>()) }

        storage(createBucket = true).afterPropertiesSet()
        verify(exactly = 1) { s3.createBucket(any<Consumer<CreateBucketRequest.Builder>>()) }
    }

    @Test
    fun `startup keeps an existing bucket and tolerates an unreachable store`() {
        every { s3.headBucket(any<Consumer<HeadBucketRequest.Builder>>()) } returns HeadBucketResponse.builder().build()
        storage(createBucket = true).afterPropertiesSet()
        verify(exactly = 0) { s3.createBucket(any<Consumer<CreateBucketRequest.Builder>>()) }

        every { s3.headBucket(any<Consumer<HeadBucketRequest.Builder>>()) } throws SdkClientException.create("refused")
        assertDoesNotThrow { storage(createBucket = true).afterPropertiesSet() }
    }

    @Test
    fun `put creates the bucket lazily in dev and retries once`() {
        every { s3.putObject(any<Consumer<PutObjectRequest.Builder>>(), any<RequestBody>()) } throws
            noSuchBucket andThen PutObjectResponse.builder().build()
        every { s3.headBucket(any<Consumer<HeadBucketRequest.Builder>>()) } throws noSuchBucket

        storage(createBucket = true).put("k", byteArrayOf(1), "image/png")

        verify(exactly = 1) { s3.createBucket(any<Consumer<CreateBucketRequest.Builder>>()) }
        verify(exactly = 2) { s3.putObject(any<Consumer<PutObjectRequest.Builder>>(), any<RequestBody>()) }
    }

    @Test
    fun `put propagates a missing bucket outside dev`() {
        every { s3.putObject(any<Consumer<PutObjectRequest.Builder>>(), any<RequestBody>()) } throws noSuchBucket

        assertThrows<NoSuchBucketException> { storage().put("k", byteArrayOf(1), "image/png") }
    }

    @Test
    fun `disabled storage reports itself unavailable`() {
        assertFalse(DisabledObjectStorage.isEnabled)
        assertThrows<StorageUnavailableException> { DisabledObjectStorage.put("k", byteArrayOf(), "x") }
        assertThrows<StorageUnavailableException> { DisabledObjectStorage.get("k") }
        assertThrows<StorageUnavailableException> { DisabledObjectStorage.delete("k") }
    }

    @Test
    fun `config requires credentials and builds a client and storage with them`() {
        val config = StorageConfig()
        assertThrows<IllegalStateException> { config.s3Client(StorageProperties(enabled = true)) }

        val properties = StorageProperties(enabled = true, accessKey = "key", secretKey = "secret")
        config.s3Client(properties).use { client ->
            assertTrue(config.s3ObjectStorage(client, properties).isEnabled)
        }
        assertFalse(config.disabledObjectStorage().isEnabled)
    }
}
