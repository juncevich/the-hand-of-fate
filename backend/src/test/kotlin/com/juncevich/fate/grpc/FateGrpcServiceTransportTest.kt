package com.juncevich.fate.grpc

import com.juncevich.fate.account.TelegramLinkService
import com.juncevich.fate.account.UserProfile
import com.juncevich.fate.account.UserQueryService
import com.juncevich.fate.vote.VoteService
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** The `StreamObserver` overrides: a body's result completes the call, its failure ends it with a status. */
class FateGrpcServiceTransportTest {
    private val userQueryService = mockk<UserQueryService>()
    private val telegramLinkService = mockk<TelegramLinkService>()
    private val service = FateGrpcService(userQueryService, telegramLinkService, mockk<VoteService>())

    @Test
    fun `a successful body completes the call with its response`() {
        every { telegramLinkService.linkAccount("token", 42, "tg") } returns
            UserProfile(UUID.randomUUID(), "owner@example.com", "Owner")
        every { telegramLinkService.unlinkAccount(42) } just runs

        val link = RecordingObserver<LinkTelegramAccountResponse>()
        service.linkTelegramAccount(
            LinkTelegramAccountRequest
                .newBuilder()
                .setLinkToken("token")
                .setTelegramId(42)
                .setTelegramName("tg")
                .build(),
            link
        )
        val unlink = RecordingObserver<UnlinkTelegramAccountResponse>()
        service.unlinkTelegramAccount(UnlinkTelegramAccountRequest.newBuilder().setTelegramId(42).build(), unlink)

        assertTrue(link.completed)
        assertTrue(link.values.single().success)
        assertNull(link.error)
        assertTrue(unlink.completed)
        assertTrue(unlink.values.single().success)
    }

    @Test
    fun `a failing body ends every call with its status`() {
        every { userQueryService.findProfileByTelegramId(7) } returns null
        val voteId = UUID.randomUUID().toString()
        val calls: List<(StreamObserver<Any>) -> Unit> =
            listOf(
                { service.getMyVotes(GetMyVotesRequest.newBuilder().setTelegramId(7).build(), it.cast()) },
                {
                    service.createVote(
                        CreateVoteRequest
                            .newBuilder()
                            .setTelegramId(7)
                            .setTitle("Vote")
                            .build(),
                        it.cast()
                    )
                },
                {
                    service.getVoteDetails(
                        GetVoteDetailsRequest
                            .newBuilder()
                            .setTelegramId(7)
                            .setVoteId(voteId)
                            .build(),
                        it.cast()
                    )
                },
                {
                    service.drawVote(
                        DrawVoteRequest
                            .newBuilder()
                            .setTelegramId(7)
                            .setVoteId(voteId)
                            .build(),
                        it.cast()
                    )
                },
                {
                    service.getLastDrawResult(
                        GetLastDrawResultRequest
                            .newBuilder()
                            .setTelegramId(7)
                            .setVoteId(voteId)
                            .build(),
                        it.cast()
                    )
                },
                {
                    service.getVoteHistory(
                        GetVoteHistoryRequest
                            .newBuilder()
                            .setTelegramId(7)
                            .setVoteId(voteId)
                            .build(),
                        it.cast()
                    )
                }
            )

        calls.forEach { call ->
            val observer = RecordingObserver<Any>()
            call(observer)
            val error = observer.error as StatusRuntimeException
            assertEquals(Status.Code.NOT_FOUND, error.status.code)
            assertTrue(observer.values.isEmpty())
            assertEquals(false, observer.completed)
        }
    }

    @Test
    fun `an unexpected failure ends the call as INTERNAL without its message`() {
        every { telegramLinkService.unlinkAccount(42) } throws IllegalStateException("secret detail")
        val observer = RecordingObserver<UnlinkTelegramAccountResponse>()

        service.unlinkTelegramAccount(UnlinkTelegramAccountRequest.newBuilder().setTelegramId(42).build(), observer)

        val error = observer.error as StatusRuntimeException
        assertEquals(Status.Code.INTERNAL, error.status.code)
        assertEquals("Unexpected error", error.status.description)
    }

    private class RecordingObserver<T> : StreamObserver<T> {
        val values = mutableListOf<T>()
        var error: Throwable? = null
        var completed = false

        override fun onNext(value: T) {
            values += value
        }

        override fun onError(t: Throwable) {
            error = t
        }

        override fun onCompleted() {
            completed = true
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> StreamObserver<Any>.cast(): StreamObserver<T> = this as StreamObserver<T>
}
