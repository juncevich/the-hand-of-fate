package com.juncevich.fate.grpc

import com.juncevich.fate.account.TelegramLinkService
import com.juncevich.fate.account.UserQueryService
import com.juncevich.fate.account.mailbox
import com.juncevich.fate.account.userId
import com.juncevich.fate.vote.CreateVoteCommand
import com.juncevich.fate.vote.VoteId
import com.juncevich.fate.vote.VoteService
import io.grpc.stub.StreamObserver
import org.springframework.data.domain.PageRequest
import org.springframework.grpc.server.service.GrpcService
import java.util.UUID
import com.juncevich.fate.shared.ErrorCode as DomainErrorCode

private const val GRPC_DEFAULT_PAGE_SIZE = 50

// Upper bound on pages fetched for a single bot listing, so a user with an
// unusually large number of votes can't force an unbounded aggregation.
private const val GRPC_MAX_PAGES = 20

/**
 * The bot's API. Each RPC runs on its own virtual thread (`GrpcServerConfig`) and calls the
 * blocking services directly; the `internal` overloads hold the bodies, the overrides only
 * complete the call ([unary]).
 */
@GrpcService
class FateGrpcService(
    private val userQueryService: UserQueryService,
    private val telegramLinkService: TelegramLinkService,
    private val voteService: VoteService,
) : FateServiceGrpc.FateServiceImplBase() {
    override fun linkTelegramAccount(
        request: LinkTelegramAccountRequest,
        responseObserver: StreamObserver<LinkTelegramAccountResponse>,
    ) = unary(responseObserver) { linkTelegramAccount(request) }

    internal fun linkTelegramAccount(request: LinkTelegramAccountRequest): LinkTelegramAccountResponse =
        grpcMutation(
            failure = { message, code ->
                LinkTelegramAccountResponse
                    .newBuilder()
                    .setSuccess(false)
                    .setMessage(message)
                    .setErrorCode(code)
                    .build()
            }
        ) {
            val user =
                telegramLinkService.linkAccount(
                    token = request.linkToken,
                    telegramId = request.telegramId,
                    telegramName = request.telegramName
                )
            LinkTelegramAccountResponse
                .newBuilder()
                .setSuccess(true)
                .setDisplayName(user.displayName)
                .setMessage("Account linked successfully!")
                .build()
        }

    override fun unlinkTelegramAccount(
        request: UnlinkTelegramAccountRequest,
        responseObserver: StreamObserver<UnlinkTelegramAccountResponse>,
    ) = unary(responseObserver) { unlinkTelegramAccount(request) }

    internal fun unlinkTelegramAccount(request: UnlinkTelegramAccountRequest): UnlinkTelegramAccountResponse =
        grpcMutation(
            failure = { message, code ->
                UnlinkTelegramAccountResponse
                    .newBuilder()
                    .setSuccess(false)
                    .setMessage(message)
                    .setErrorCode(code)
                    .build()
            }
        ) {
            telegramLinkService.unlinkAccount(request.telegramId)
            UnlinkTelegramAccountResponse
                .newBuilder()
                .setSuccess(true)
                .setMessage("Account unlinked.")
                .build()
        }

    override fun getMyVotes(
        request: GetMyVotesRequest,
        responseObserver: StreamObserver<GetMyVotesResponse>,
    ) = unary(responseObserver) { getMyVotes(request) }

    internal fun getMyVotes(request: GetMyVotesRequest): GetMyVotesResponse {
        val user = linkedUser(request.telegramId)
        val votes =
            buildList {
                var pageNumber = 0
                do {
                    val page =
                        voteService.listVotes(
                            user.userId,
                            user.mailbox,
                            PageRequest.of(pageNumber, GRPC_DEFAULT_PAGE_SIZE)
                        )
                    addAll(page.content)
                    pageNumber++
                } while (page.hasNext() && pageNumber < GRPC_MAX_PAGES)
            }
        val summaries =
            votes.map { dto ->
                VoteSummary
                    .newBuilder()
                    .setVoteId(dto.id.toString())
                    .setTitle(dto.title)
                    .setStatus(dto.status.toProto())
                    .setMode(dto.mode.toProto())
                    .setParticipantCount(dto.participantCount.toInt())
                    .setIsCreator(dto.isCreator)
                    .setCurrentRound(dto.currentRound)
                    .build()
            }
        return GetMyVotesResponse.newBuilder().addAllVotes(summaries).build()
    }

    override fun createVote(
        request: CreateVoteRequest,
        responseObserver: StreamObserver<CreateVoteResponse>,
    ) = unary(responseObserver) { createVote(request) }

    internal fun createVote(request: CreateVoteRequest): CreateVoteResponse {
        val user = linkedUser(request.telegramId)
        val title = request.title.trim()
        if (title.isBlank()) {
            throw GrpcErrors.invalidArgument(DomainErrorCode.TITLE_INVALID, "Vote title is required")
        }
        val mode = request.mode.toDomain()
        val participantEmails = request.participantEmailsList
        val options = request.optionsList

        return grpcMutation(
            failure = { message, code ->
                CreateVoteResponse
                    .newBuilder()
                    .setSuccess(false)
                    .setMessage(message)
                    .setErrorCode(code)
                    .build()
            }
        ) {
            val vote =
                voteService.createVote(
                    creatorId = user.userId,
                    request =
                        CreateVoteCommand(
                            title = title,
                            description = request.description.takeIf { it.isNotBlank() },
                            mode = mode,
                            participantEmails = participantEmails,
                            options = options,
                            drawTarget = request.drawTarget.toDomain()
                        )
                )

            CreateVoteResponse
                .newBuilder()
                .setSuccess(true)
                .setMessage("Vote created")
                .setVote(vote.toProto())
                .build()
        }
    }

    override fun getVoteDetails(
        request: GetVoteDetailsRequest,
        responseObserver: StreamObserver<GetVoteDetailsResponse>,
    ) = unary(responseObserver) { getVoteDetails(request) }

    internal fun getVoteDetails(request: GetVoteDetailsRequest): GetVoteDetailsResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)
        val voteDto =
            grpcRead {
                voteService.getVote(voteId, user.userId, user.mailbox)
            }
        return voteDto.toProto()
    }

    override fun drawVote(
        request: DrawVoteRequest,
        responseObserver: StreamObserver<DrawVoteResponse>,
    ) = unary(responseObserver) { drawVote(request) }

    internal fun drawVote(request: DrawVoteRequest): DrawVoteResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)
        return grpcMutation(
            failure = { message, code ->
                DrawVoteResponse
                    .newBuilder()
                    .setSuccess(false)
                    .setMessage(message)
                    .setErrorCode(code)
                    .build()
            }
        ) {
            val result = voteService.draw(voteId, user.userId)
            DrawVoteResponse
                .newBuilder()
                .setSuccess(true)
                .setWinnerEmail(result.winner.email)
                .setWinnerDisplayName(result.winner.displayName)
                .setWinnerOptionTitle(result.winner.optionTitle)
                .setRound(result.round)
                .setNewRoundStarted(result.newRoundStarted)
                .setMessage("✦ The Hand of Fate has chosen: ${result.winner.label}")
                .build()
        }
    }

    override fun getLastDrawResult(
        request: GetLastDrawResultRequest,
        responseObserver: StreamObserver<GetLastDrawResultResponse>,
    ) = unary(responseObserver) { getLastDrawResult(request) }

    internal fun getLastDrawResult(request: GetLastDrawResultRequest): GetLastDrawResultResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)

        val lastDraw =
            grpcRead {
                voteService.getLastResult(voteId, user.userId, user.mailbox)
            }

        return if (lastDraw == null) {
            GetLastDrawResultResponse.newBuilder().setHasResult(false).build()
        } else {
            GetLastDrawResultResponse
                .newBuilder()
                .setHasResult(true)
                .setResult(lastDraw.toDrawResultInfo())
                .build()
        }
    }

    override fun getVoteHistory(
        request: GetVoteHistoryRequest,
        responseObserver: StreamObserver<GetVoteHistoryResponse>,
    ) = unary(responseObserver) { getVoteHistory(request) }

    internal fun getVoteHistory(request: GetVoteHistoryRequest): GetVoteHistoryResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)
        val history =
            grpcRead {
                voteService.getHistory(voteId, user.userId, user.mailbox, historyPage(request))
            }
        return GetVoteHistoryResponse
            .newBuilder()
            .addAllResults(history.content.map { it.toDrawResultInfo() })
            .setTotalPages(history.totalPages)
            .setTotalElements(history.totalElements)
            .build()
    }

    private fun historyPage(request: GetVoteHistoryRequest): PageRequest {
        val size = if (request.pageSize == 0) 20 else request.pageSize
        if (request.page < 0 || size !in 1..100) {
            throw GrpcErrors.invalidArgument(DomainErrorCode.PAGE_INVALID, "Invalid history pagination")
        }
        return PageRequest.of(request.page, size)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun linkedUser(telegramId: Long) =
        userQueryService.findProfileByTelegramId(telegramId)
            ?: throw GrpcErrors.telegramNotLinked()

    private fun parseVoteId(value: String): VoteId =
        runCatching { VoteId(UUID.fromString(value)) }
            .getOrElse { throw GrpcErrors.invalidArgument(DomainErrorCode.VOTE_ID_INVALID, "Invalid vote id") }
}
