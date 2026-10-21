package com.juncevich.fate.auth

import com.juncevich.fate.AbstractApiIntegrationTest
import com.juncevich.fate.auth.internal.service.IdentityDeletionWorker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.time.Duration
import java.util.UUID

/**
 * The backend against a real Keycloak configured from infra/keycloak/ exactly as every environment
 * is (keycloak-config-cli): its tokens are accepted and the `fate-backend` service account may do
 * what the backend asks of the Admin API. The other tests use FakeKeycloak.
 */
@ContextConfiguration(initializers = [KeycloakSmokeIntegrationTest.RealKeycloak::class])
class KeycloakSmokeIntegrationTest
    @Autowired
    constructor(
        private val identityDeletionWorker: IdentityDeletionWorker,
    ) : AbstractApiIntegrationTest() {
        private val keycloakApi = RestClient.create(keycloakUrl)

        private fun token(
            realm: String,
            form: String,
        ): String =
            checkNotNull(
                keycloakApi
                    .post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body<Map<String, Any>>()
            )["access_token"] as String

        private fun adminToken(): String = token("master", "grant_type=password&client_id=admin-cli&username=admin&password=admin")

        /** Creates a verified user in realm `fate`, as registration plus email confirmation would. */
        private fun createKeycloakUser(
            email: String,
            password: String,
        ): UUID {
            val location =
                keycloakApi
                    .post()
                    .uri("/admin/realms/fate/users")
                    .header("Authorization", "Bearer ${adminToken()}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        """
                        {"username":"$email","email":"$email","emailVerified":true,"enabled":true,
                         "firstName":"Smoke","lastName":"Test",
                         "credentials":[{"type":"password","value":"$password","temporary":false}]}
                        """.trimIndent()
                    ).retrieve()
                    .toBodilessEntity()
                    .headers.location
            return UUID.fromString(checkNotNull(location).path.substringAfterLast('/'))
        }

        /** A token for the user through `fate-test`, the dev/e2e client with direct grants. */
        private fun userToken(
            email: String,
            password: String,
        ): String = token("fate", "grant_type=password&client_id=fate-test&username=$email&password=$password&scope=openid")

        @Test
        fun `Keycloak's tokens are accepted and its service account can end sessions and delete users`() {
            val email = "smoke-${UUID.randomUUID()}@test.com"
            val id = createKeycloakUser(email, "smoke-password")
            val token = userToken(email, "smoke-password")

            mockMvc
                .get("/api/v1/users/me/email-preferences") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
            assertEquals(email, jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String::class.java, id))
            assertEquals("Smoke Test", jdbcTemplate.queryForObject("SELECT display_name FROM users WHERE id = ?", String::class.java, id))

            // Admin API: end the user's sessions (manage-users)…
            mockMvc
                .post("/api/v1/auth/logout-all") { header("Authorization", "Bearer $token") }
                .andExpect { status { isNoContent() } }

            // …and delete the user once the account is deleted here.
            jdbcTemplate.update("INSERT INTO deleted_accounts (user_id, deleted_at) VALUES (?, now())", id)
            identityDeletionWorker.run()

            val lookup =
                runCatching {
                    keycloakApi
                        .get()
                        .uri("/admin/realms/fate/users/{id}", id)
                        .header("Authorization", "Bearer ${adminToken()}")
                        .retrieve()
                        .toBodilessEntity()
                }
            assertEquals(404, (lookup.exceptionOrNull() as HttpClientErrorException).statusCode.value())
            assertEquals(
                1,
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM deleted_accounts WHERE user_id = ? AND identity_deleted_at IS NOT NULL",
                    Int::class.java,
                    id
                )
            )
        }

        companion object {
            private const val KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.0"
            private const val CONFIG_CLI_IMAGE = "adorsys/keycloak-config-cli:6.5.1-26"
            private const val BACKEND_SECRET = "smoke-test-backend-secret"

            private val network: Network = Network.newNetwork()

            // Without KC_HOSTNAME a dev-mode Keycloak names itself after the request, so tokens fetched
            // through the mapped port carry an issuer the backend can reach too.
            private val keycloak: GenericContainer<*> =
                GenericContainer(KEYCLOAK_IMAGE)
                    .withNetwork(network)
                    .withNetworkAliases("keycloak")
                    .withCommand("start-dev")
                    .withEnv("KC_HTTP_RELATIVE_PATH", "/auth")
                    .withEnv("KC_HEALTH_ENABLED", "true")
                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                    .withExposedPorts(8080, 9000)
                    .waitingFor(Wait.forHttp("/auth/health/ready").forPort(9000).withStartupTimeout(Duration.ofMinutes(3)))
                    .also { it.start() }

            private val keycloakUrl = "http://${keycloak.host}:${keycloak.getMappedPort(8080)}/auth"

            init {
                val config = File("../infra/keycloak").canonicalPath
                GenericContainer(CONFIG_CLI_IMAGE)
                    .withNetwork(network)
                    .withCreateContainerCmdModifier { it.withEntrypoint("/bin/sh", "/scripts/apply-config.sh") }
                    .withFileSystemBind("$config/apply-config.sh", "/scripts/apply-config.sh", BindMode.READ_ONLY)
                    .withFileSystemBind("$config/realm", "/config/realm", BindMode.READ_ONLY)
                    .withEnv("KEYCLOAK_URL", "http://keycloak:8080/auth")
                    .withEnv("KEYCLOAK_USER", "admin")
                    .withEnv("KEYCLOAK_PASSWORD", "admin")
                    .withEnv("IMPORT_VARSUBSTITUTION_ENABLED", "true")
                    .withEnv("IMPORT_FILES_LOCATIONS", "file:/config/realm/*")
                    .withEnv("KC_APP_URL", "http://localhost:3000")
                    .withEnv("KC_BACKEND_CLIENT_SECRET", BACKEND_SECRET)
                    .withEnv("KC_DEV_CLIENTS_ENABLED", "true")
                    // The plain image has no `fate` theme (that is the fate-keycloak image)
                    .withEnv("KC_THEME", "keycloak")
                    .withStartupCheckStrategy(OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(3)))
                    .start()
            }
        }

        // Ahead of the base class's @DynamicPropertySource, which points at FakeKeycloak and would win
        // over a subclass registering the same properties.
        class RealKeycloak : ApplicationContextInitializer<ConfigurableApplicationContext> {
            override fun initialize(context: ConfigurableApplicationContext) {
                context.environment.propertySources.addFirst(
                    MapPropertySource(
                        "real-keycloak",
                        mapOf(
                            "app.keycloak.url" to keycloakUrl,
                            "app.keycloak.public-url" to keycloakUrl,
                            "app.keycloak.client-secret" to BACKEND_SECRET
                        )
                    )
                )
            }
        }
    }
