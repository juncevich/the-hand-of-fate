package com.juncevich.fate.shared.internal.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Fail-fast guard against shipping insecure defaults. Active in every profile
 * except `dev`/`test`, so a production boot aborts if the JWT signing key or the
 * gRPC shared secret is missing, too short, or left at the checked-in dev value.
 */
@Component
@Profile("!dev & !test")
class SecretsValidator(
    @param:Value("\${jwt.access-secret}") private val jwtAccessSecret: String,
    @param:Value("\${grpc.shared-secret}") private val grpcSharedSecret: String,
) : InitializingBean {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterPropertiesSet() {
        val problems =
            buildList {
                if (jwtAccessSecret in KNOWN_DEFAULTS) {
                    add("JWT_ACCESS_SECRET is left at the insecure development default")
                }
                if (jwtAccessSecret.toByteArray().size < MIN_JWT_SECRET_BYTES) {
                    add("JWT_ACCESS_SECRET must be at least $MIN_JWT_SECRET_BYTES bytes (256 bits)")
                }
                if (grpcSharedSecret in KNOWN_DEFAULTS) {
                    add("GRPC_SHARED_SECRET is left at the insecure development default")
                }
                if (grpcSharedSecret.length < MIN_SHARED_SECRET_LENGTH) {
                    add("GRPC_SHARED_SECRET must be at least $MIN_SHARED_SECRET_LENGTH characters")
                }
            }

        if (problems.isNotEmpty()) {
            problems.forEach { log.error("Insecure configuration: {}", it) }
            error(
                "Refusing to start with insecure secrets. Fix the following or run with the 'dev' profile: " +
                    problems.joinToString("; ")
            )
        }
    }

    private companion object {
        const val MIN_JWT_SECRET_BYTES = 32
        const val MIN_SHARED_SECRET_LENGTH = 16

        // Defaults baked into application.yml / docker-compose for local development.
        val KNOWN_DEFAULTS =
            setOf(
                "changeme-dev-secret-key-256-bits-long-xxxxxxxxxxxx",
                "changeme-dev-grpc-shared-secret",
                "dev-secret-key-at-least-256-bits-long-for-testing-purposes-only",
                "dev-grpc-shared-secret-for-testing-only"
            )
    }
}
