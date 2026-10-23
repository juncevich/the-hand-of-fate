package com.juncevich.fate.platform

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Fail-fast guard against shipping insecure defaults. Active in every profile
 * except `dev`/`test`, so a production boot aborts if the gRPC shared secret, the
 * unsubscribe-link secret or the Keycloak client secret is missing, too short, or left at the
 * checked-in dev value.
 */
@Component
@Profile("!dev & !test")
class SecretsValidator(
    @param:Value("\${grpc.shared-secret}") private val grpcSharedSecret: String,
    @param:Value("\${app.notifications.unsubscribe-secret}") private val unsubscribeSecret: String,
    @param:Value("\${app.keycloak.client-secret}") private val keycloakClientSecret: String,
) : InitializingBean {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterPropertiesSet() {
        val problems =
            buildList {
                if (grpcSharedSecret in KNOWN_DEFAULTS) {
                    add("GRPC_SHARED_SECRET is left at the insecure development default")
                }
                if (grpcSharedSecret.length < MIN_SHARED_SECRET_LENGTH) {
                    add("GRPC_SHARED_SECRET must be at least $MIN_SHARED_SECRET_LENGTH characters")
                }
                if (unsubscribeSecret in KNOWN_DEFAULTS) {
                    add("UNSUBSCRIBE_SECRET is left at the insecure development default")
                }
                if (unsubscribeSecret.toByteArray().size < MIN_SECRET_BYTES) {
                    add("UNSUBSCRIBE_SECRET must be at least $MIN_SECRET_BYTES bytes (256 bits)")
                }
                if (keycloakClientSecret in KNOWN_DEFAULTS) {
                    add("KEYCLOAK_CLIENT_SECRET is left at the insecure development default")
                }
                if (keycloakClientSecret.length < MIN_SHARED_SECRET_LENGTH) {
                    add("KEYCLOAK_CLIENT_SECRET must be at least $MIN_SHARED_SECRET_LENGTH characters")
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
        const val MIN_SECRET_BYTES = 32
        const val MIN_SHARED_SECRET_LENGTH = 16

        // Defaults baked into application.yml / docker-compose for local development.
        val KNOWN_DEFAULTS =
            setOf(
                "changeme-dev-grpc-shared-secret",
                "changeme-dev-unsubscribe-secret",
                "dev-grpc-shared-secret-for-testing-only",
                "dev-keycloak-backend-secret"
            )
    }
}
