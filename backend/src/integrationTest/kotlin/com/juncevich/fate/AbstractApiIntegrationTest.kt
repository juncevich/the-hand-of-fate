package com.juncevich.fate

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "spring.grpc.server.enabled=false",
        // Disable mail health indicator to avoid SMTP connection attempts during health checks
        "management.health.mail.enabled=false"
    ]
)
@ActiveProfiles("test")
// Shared base for API tests: abstract so it is never instantiated/run on its own,
// even though it declares no abstract members.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AbstractApiIntegrationTest {
    protected lateinit var mockMvc: MockMvc

    // The application's own Jackson 3 mapper, so tests parse JSON exactly as the app writes it
    @Autowired
    protected lateinit var objectMapper: JsonMapper

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUpMockMvc(
        @Autowired context: WebApplicationContext,
    ) {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    companion object {
        private const val STORAGE_KEY = "test-storage-key"

        private val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17").also { it.start() }

        // Same S3-compatible server as local development (docker-compose.infra.yml).
        private val rustfs: GenericContainer<*> =
            GenericContainer("rustfs/rustfs:1.0.1")
                .withEnv("RUSTFS_VOLUMES", "/data")
                .withEnv("RUSTFS_ADDRESS", "0.0.0.0:9000")
                .withEnv("RUSTFS_ACCESS_KEY", STORAGE_KEY)
                .withEnv("RUSTFS_SECRET_KEY", STORAGE_KEY)
                .withExposedPorts(9000)
                .waitingFor(Wait.forListeningPort())
                .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun datasource(registry: DynamicPropertyRegistry) {
            // Plain JDBC URL, exactly as production configures it (no stringtype=unspecified),
            // so enum/column type mismatches surface here instead of in production.
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("app.storage.enabled") { true }
            registry.add("app.storage.create-bucket") { true }
            registry.add("app.storage.endpoint") { "http://${rustfs.host}:${rustfs.getMappedPort(9000)}" }
            registry.add("app.storage.access-key") { STORAGE_KEY }
            registry.add("app.storage.secret-key") { STORAGE_KEY }
            // Keycloak's keys and Admin API come from FakeKeycloak; tokens carry its public issuer.
            registry.add("app.keycloak.url") { FakeKeycloak.url }
            registry.add("app.keycloak.public-url") { FakeKeycloak.PUBLIC_URL }
        }
    }

    // ── Auth helpers ─────────────────────────────────────────────────────────

    /**
     * Signs [email] up as Keycloak would and makes the first request, which creates the account;
     * returns the access token.
     */
    protected fun signUp(
        email: String,
        displayName: String = "Test User",
    ): String {
        val token = keycloakToken(email, name = displayName)
        mockMvc
            .get("/api/v1/users/me") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
        return token
    }

    /**
     * An access token Keycloak would issue to the SPA for a user it holds (see [FakeKeycloak]); the
     * backend creates the account on its first request.
     */
    protected fun keycloakToken(
        email: String,
        userId: UUID = UUID.randomUUID(),
        authTime: Instant? = Instant.now(),
        name: String = "Kate Keycloak",
    ): String {
        FakeKeycloak.users += userId
        return FakeKeycloak.token(userId, email, name = name, authTime = authTime)
    }

    protected fun parse(json: String): JsonNode = objectMapper.readTree(json)

    protected fun JsonNode.text(field: String): String = this[field].asString()
}
