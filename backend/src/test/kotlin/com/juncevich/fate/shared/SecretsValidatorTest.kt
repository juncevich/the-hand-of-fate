package com.juncevich.fate.shared

import com.juncevich.fate.shared.internal.config.SecretsValidator
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SecretsValidatorTest {
    private val strongSecret = "a".repeat(48)

    @Test
    fun `rejects the checked-in dev jwt default`() {
        val validator =
            SecretsValidator(
                jwtAccessSecret = "changeme-dev-secret-key-256-bits-long-xxxxxxxxxxxx",
                grpcSharedSecret = strongSecret
            )
        assertThrows<IllegalStateException> { validator.afterPropertiesSet() }
    }

    @Test
    fun `rejects a too-short jwt secret`() {
        val validator = SecretsValidator(jwtAccessSecret = "short", grpcSharedSecret = strongSecret)
        assertThrows<IllegalStateException> { validator.afterPropertiesSet() }
    }

    @Test
    fun `rejects the dev grpc shared secret default`() {
        val validator =
            SecretsValidator(
                jwtAccessSecret = strongSecret,
                grpcSharedSecret = "dev-grpc-shared-secret-for-testing-only"
            )
        assertThrows<IllegalStateException> { validator.afterPropertiesSet() }
    }

    @Test
    fun `accepts strong non-default secrets`() {
        val validator =
            SecretsValidator(
                jwtAccessSecret = strongSecret,
                grpcSharedSecret = "another-strong-shared-secret"
            )
        assertDoesNotThrow { validator.afterPropertiesSet() }
    }
}
