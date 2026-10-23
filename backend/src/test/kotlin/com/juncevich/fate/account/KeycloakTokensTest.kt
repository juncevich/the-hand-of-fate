package com.juncevich.fate.account

import com.juncevich.fate.account.keycloak.KeycloakAccessTokenConverter
import com.juncevich.fate.account.keycloak.KeycloakIdentity
import com.juncevich.fate.account.keycloak.KeycloakUserProvisioner
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import java.time.Instant
import java.util.UUID

class KeycloakTokensTest {
    private val provisioner = mockk<KeycloakUserProvisioner>()
    private val converter = KeycloakAccessTokenConverter(provisioner)
    private val id = UUID.randomUUID()

    private fun jwt(authTime: Instant? = null) =
        Jwt
            .withTokenValue("t")
            .header("alg", "RS256")
            .subject(id.toString())
            .claim("email", " Kate@Test.com ")
            .claim("name", "Kate")
            .apply { authTime?.let { claim("auth_time", it) } }
            .build()

    @Test
    fun `exposes the token's user with a canonical email and the sign-in time`() {
        val signedInAt = Instant.parse("2026-10-20T10:00:00Z")
        every { provisioner.ensureProvisioned(KeycloakIdentity(id, "kate@test.com", "Kate")) } returns true

        val principal = converter.convert(jwt(signedInAt)).principal as AuthenticatedUser

        assertEquals(AuthenticatedUser(id, "kate@test.com", signedInAt), principal)
    }

    @Test
    fun `rejects the token of a deleted account`() {
        every { provisioner.ensureProvisioned(any()) } returns false

        assertThrows<InvalidBearerTokenException> { converter.convert(jwt()) }
    }
}
