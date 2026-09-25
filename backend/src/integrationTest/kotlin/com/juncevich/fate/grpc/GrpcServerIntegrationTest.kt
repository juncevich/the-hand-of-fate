package com.juncevich.fate.grpc

import com.juncevich.fate.AbstractApiIntegrationTest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.get
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Exercises the real gRPC server over TCP — server wiring, service registration and the
 * shared-secret interceptor — rather than calling [FateGrpcService] methods directly.
 */
class GrpcServerIntegrationTest : AbstractApiIntegrationTest() {
    companion object {
        private const val SHARED_SECRET = "integration-test-grpc-shared-secret"
        private val port: Int = ServerSocket(0).use { it.localPort }

        @DynamicPropertySource
        @JvmStatic
        fun grpcServer(registry: DynamicPropertyRegistry) {
            registry.add("spring.grpc.server.enabled") { true }
            registry.add("spring.grpc.server.port") { port }
            registry.add("spring.grpc.server.address") { "127.0.0.1" }
            registry.add("grpc.shared-secret") { SHARED_SECRET }
        }
    }

    private lateinit var channel: ManagedChannel
    private lateinit var stub: FateServiceGrpcKt.FateServiceCoroutineStub

    @BeforeEach
    fun openChannel() {
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", port).usePlaintext().build()
        stub = FateServiceGrpcKt.FateServiceCoroutineStub(channel)
    }

    @AfterEach
    fun closeChannel() {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun secret(value: String) = Metadata().apply { put(SharedSecretAuthInterceptor.SHARED_SECRET_KEY, value) }

    private fun getMyVotes(telegramId: Long) = GetMyVotesRequest.newBuilder().setTelegramId(telegramId).build()

    @Test
    fun `call without shared secret is rejected as UNAUTHENTICATED`() {
        val ex = assertThrows<StatusException> { runBlocking { stub.getMyVotes(getMyVotes(1)) } }
        assertEquals(Status.Code.UNAUTHENTICATED, ex.status.code)
    }

    @Test
    fun `call with wrong shared secret is rejected as UNAUTHENTICATED`() {
        val ex =
            assertThrows<StatusException> {
                runBlocking { stub.getMyVotes(getMyVotes(1), secret("wrong-secret")) }
            }
        assertEquals(Status.Code.UNAUTHENTICATED, ex.status.code)
    }

    @Test
    fun `call with valid secret reaches the service`() {
        val ex =
            assertThrows<StatusException> {
                runBlocking { stub.getMyVotes(getMyVotes(Random.nextLong(1, Long.MAX_VALUE)), secret(SHARED_SECRET)) }
            }
        assertEquals(Status.Code.NOT_FOUND, ex.status.code)
        assertEquals("Telegram account not linked", ex.status.description)
    }

    @Test
    fun `linked telegram user can create, draw and list votes over gRPC`() {
        val accessToken = registerAndGetToken("grpc-${UUID.randomUUID()}@test.com")
        val linkToken =
            parse(
                mockMvc
                    .get("/api/v1/telegram/link-token") { header("Authorization", "Bearer $accessToken") }
                    .andReturn()
                    .response.contentAsString
            ).text("token")
        val telegramId = Random.nextLong(1, Long.MAX_VALUE)
        val headers = secret(SHARED_SECRET)

        runBlocking {
            val link =
                stub.linkTelegramAccount(
                    LinkTelegramAccountRequest
                        .newBuilder()
                        .setLinkToken(linkToken)
                        .setTelegramId(telegramId)
                        .setTelegramName("tester")
                        .build(),
                    headers
                )
            assertTrue(link.success, link.message)

            val created =
                stub.createVote(
                    CreateVoteRequest
                        .newBuilder()
                        .setTelegramId(telegramId)
                        .setTitle("gRPC vote")
                        .setMode(VoteMode.VOTE_MODE_SIMPLE)
                        .addAllOptions(listOf("Alpha", "Beta"))
                        .build(),
                    headers
                )
            assertTrue(created.success, created.message)
            val voteId = created.vote.voteId

            val draw =
                stub.drawVote(
                    DrawVoteRequest
                        .newBuilder()
                        .setTelegramId(telegramId)
                        .setVoteId(voteId)
                        .build(),
                    headers
                )
            assertTrue(draw.success, draw.message)
            assertTrue(draw.winnerOptionTitle in setOf("Alpha", "Beta"))

            val votes = stub.getMyVotes(getMyVotes(telegramId), headers)
            val summary = votes.votesList.single { it.voteId == voteId }
            assertEquals(VoteStatus.VOTE_STATUS_DRAWN, summary.status)
            assertTrue(summary.isCreator)

            val invalid =
                assertThrows<StatusException> {
                    stub.getVoteDetails(
                        GetVoteDetailsRequest
                            .newBuilder()
                            .setTelegramId(telegramId)
                            .setVoteId("not-a-uuid")
                            .build(),
                        headers
                    )
                }
            assertEquals(Status.Code.INVALID_ARGUMENT, invalid.status.code)
            assertFalse(invalid.status.description.isNullOrBlank())
        }
    }
}
