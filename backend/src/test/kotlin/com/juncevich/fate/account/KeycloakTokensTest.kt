package com.juncevich.fate.account

import com.juncevich.fate.account.keycloak.KeycloakAccessTokenConverter
import com.juncevich.fate.account.keycloak.KeycloakAuthentication
import com.juncevich.fate.account.keycloak.KeycloakIdentity
import com.juncevich.fate.account.keycloak.KeycloakUserProvisioner
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import java.time.Instant
import java.util.UUID

class KeycloakTokensTest {
    private val provisioner = mockk<KeycloakUserProvisioner>()
    private val converter = KeycloakAccessTokenConverter(provisioner)
    private val id = UserId(UUID.randomUUID())

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
        every { provisioner.ensureProvisioned(KeycloakIdentity(id, Email.of("kate@test.com"), "Kate")) } returns true

        val authentication = converter.convert(jwt(signedInAt)) as KeycloakAuthentication
        assertTrue(authentication.isAuthenticated)
        assertEquals(id.toString(), authentication.name)
        assertEquals(listOf("ROLE_USER"), authentication.authorities.map { it.authority })
        assertEquals(id.toString(), authentication.credentials.subject)
        val principal = authentication.principal

        assertEquals(AuthenticatedUser(id, Email.of("kate@test.com"), signedInAt), principal)
    }

    @Test
    fun `rejects the token of a deleted account`() {
        every { provisioner.ensureProvisioned(any()) } returns false

        assertThrows<InvalidBearerTokenException> { converter.convert(jwt()) }
    }
}
