package com.juncevich.fate

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

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
        }
    }

    // ── Auth helpers ─────────────────────────────────────────────────────────

    /** Registers, confirms the address with the mailed token and returns the session's access token. */
    protected fun registerAndGetToken(
        email: String,
        password: String = "password123",
        displayName: String = "Test User",
    ): String {
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"email":"$email","password":"$password","displayName":"$displayName"}"""
            }.andExpect { status { isAccepted() } }
        return verifyEmail(mailedToken(email, "EMAIL_VERIFICATION"))
    }

    /** Follows a verification link: returns the access token of the session it starts. */
    protected fun verifyEmail(token: String): String {
        val result =
            mockMvc
                .post("/api/v1/auth/verify-email") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"token":"$token"}"""
                }.andExpect { status { isOk() } }
                .andReturn()
        return objectMapper.readTree(result.response.contentAsString)["accessToken"].asString()
    }

    /**
     * The raw token of the latest [type] email queued for [email] — what the recipient would find
     * in the link. Tests run with outbox delivery off, so the job is still there.
     */
    protected fun mailedToken(
        email: String,
        type: String,
    ): String =
        checkNotNull(
            jdbcTemplate.queryForObject(
                """
                SELECT payload::jsonb ->> 'token' FROM notification_outbox
                WHERE event_type = ? AND payload::jsonb ->> 'recipientEmail' = ?
                ORDER BY created_at DESC, id DESC LIMIT 1
                """,
                String::class.java,
                type,
                email
            )
        )

    protected fun loginAndGetTokens(
        email: String,
        password: String = "password123",
    ): Pair<String, String> {
        val result =
            mockMvc
                .post("/api/v1/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","password":"$password"}"""
                }.andReturn()
        val accessToken = objectMapper.readTree(result.response.contentAsString)["accessToken"].asString()
        val setCookie = result.response.getHeader("Set-Cookie").orEmpty()
        val refreshToken =
            Regex("fate_refresh_token=([^;]+)")
                .find(setCookie)
                ?.groupValues
                ?.get(1)
                .orEmpty()
        return accessToken to refreshToken
    }

    protected fun parse(json: String): JsonNode = objectMapper.readTree(json)

    protected fun JsonNode.text(field: String): String = this[field].asString()
}
