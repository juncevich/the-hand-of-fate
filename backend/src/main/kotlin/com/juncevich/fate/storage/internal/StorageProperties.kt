package com.juncevich.fate.storage.internal

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * S3-compatible object storage. Off by default so a deployment without a bucket still boots;
 * the dev profile points it at the local RustFS container (docker-compose.infra.yml).
 */
@ConfigurationProperties(prefix = "app.storage")
data class StorageProperties(
    val enabled: Boolean = false,
    val endpoint: String = "http://localhost:9000",
    val region: String = "us-east-1",
    val bucket: String = "fate",
    val accessKey: String = "",
    val secretKey: String = "",
    /** Path-style addressing (`endpoint/bucket/key`) — required by most self-hosted S3 servers. */
    val pathStyle: Boolean = true,
    /** Create [bucket] on startup when missing. Meant for local development only. */
    val createBucket: Boolean = false,
)
