package com.juncevich.fate.account

import com.juncevich.fate.AbstractApiIntegrationTest
import com.juncevich.fate.FakeKeycloak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * The service account's token for Keycloak's Admin API, held by Spring Security's OAuth2 client
 * (`KeycloakAdminConfig`): one for the whole service, renewed once Keycloak rejects it.
 */
class KeycloakAdminClientIntegrationTest : AbstractApiIntegrationTest() {
    private fun logoutAll(token: String) =
        mockMvc
            .post("/api/v1/auth/logout-all") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNoContent() } }

    @Test
    fun `users signed in as themselves share the service account's token`() {
        val first = signUp("admin-a-${UUID.randomUUID()}@test.com")
        val second = signUp("admin-b-${UUID.randomUUID()}@test.com")
        logoutAll(first)
        val issued = FakeKeycloak.serviceTokensIssued.get()

        logoutAll(second)
        logoutAll(first)

        assertEquals(issued, FakeKeycloak.serviceTokensIssued.get())
    }

    @Test
    fun `a token Keycloak rejects is dropped, and the next call fetches a new one`() {
        val token = signUp("admin-c-${UUID.randomUUID()}@test.com")
        logoutAll(token)
        val issued = FakeKeycloak.serviceTokensIssued.get()
        FakeKeycloak.revokeServiceTokens()

        // The call that meets the rejection fails (an unexpected error, 500)…
        mockMvc
            .post("/api/v1/auth/logout-all") { header("Authorization", "Bearer $token") }
            .andExpect { status { isInternalServerError() } }
        // …and the next one goes out with a fresh token.
        logoutAll(token)

        assertEquals(issued + 1, FakeKeycloak.serviceTokensIssued.get())
    }
}
