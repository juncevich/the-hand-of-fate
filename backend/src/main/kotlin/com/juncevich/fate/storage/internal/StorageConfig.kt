package com.juncevich.fate.storage.internal

import com.juncevich.fate.storage.ObjectStorage
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.Duration

@Configuration
class StorageConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnBooleanProperty("app.storage.enabled")
    fun s3Client(properties: StorageProperties): S3Client {
        check(properties.accessKey.isNotBlank() && properties.secretKey.isNotBlank()) {
            "app.storage.access-key and app.storage.secret-key are required when storage is enabled"
        }
        return S3Client
            .builder()
            .endpointOverride(URI.create(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey, properties.secretKey))
            ).forcePathStyle(properties.pathStyle)
            // Newer SDKs add CRC checksums to every request by default; several S3-compatible
            // servers reject or ignore them, so only send them where the API requires it.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(
                UrlConnectionHttpClient
                    .builder()
                    .connectionTimeout(CONNECT_TIMEOUT)
                    .socketTimeout(SOCKET_TIMEOUT)
            ).build()
    }

    @Bean
    @ConditionalOnBooleanProperty("app.storage.enabled")
    fun s3ObjectStorage(
        s3Client: S3Client,
        properties: StorageProperties,
    ): ObjectStorage = S3ObjectStorage(s3Client, properties)

    @Bean
    @ConditionalOnBooleanProperty("app.storage.enabled", havingValue = false, matchIfMissing = true)
    fun disabledObjectStorage(): ObjectStorage = DisabledObjectStorage

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val SOCKET_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
