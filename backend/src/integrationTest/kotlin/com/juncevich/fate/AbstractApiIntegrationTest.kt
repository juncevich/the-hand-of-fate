package com.juncevich.fate

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "grpc.server.port=-1",
        // Disable mail health indicator to avoid SMTP connection attempts during health checks
        "management.health.mail.enabled=false"
    ]
)
abstract class AbstractApiIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    protected lateinit var mockMvc: MockMvc

    // ObjectMapper is no longer a Spring bean in Spring Boot 4; create directly
    protected val objectMapper: ObjectMapper =
        ObjectMapper()
            .registerModule(kotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @BeforeEach
    fun setUpMockMvc() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun datasource(registry: DynamicPropertyRegistry) {
            val url = postgres.jdbcUrl
            val sep = if ("?" in url) "&" else "?"
            registry.add("spring.datasource.url") { "$url${sep}stringtype=unspecified" }
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    // ── Auth helpers ─────────────────────────────────────────────────────────

    protected fun registerAndGetToken(
        email: String,
        password: String = "password123",
        displayName: String = "Test User",
    ): String {
        val result =
            mockMvc
                .post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"email":"$email","password":"$password","displayName":"$displayName"}"""
                }.andReturn()
        return objectMapper.readTree(result.response.contentAsString)["accessToken"].asText()
    }

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
        val accessToken = objectMapper.readTree(result.response.contentAsString)["accessToken"].asText()
        val setCookie = result.response.getHeader("Set-Cookie") ?: ""
        val refreshToken =
            Regex("fate_refresh_token=([^;]+)").find(setCookie)?.groupValues?.get(1) ?: ""
        return accessToken to refreshToken
    }

    protected fun parse(json: String): JsonNode = objectMapper.readTree(json)

    protected fun JsonNode.text(field: String): String = this[field].asText()
}
