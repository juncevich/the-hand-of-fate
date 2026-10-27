package com.juncevich.fate.grpc

import com.juncevich.fate.account.TelegramLinkService
import com.juncevich.fate.account.UserQueryService
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
                linkTelegramAccountResponse {
                    success = false
                    this.message = message
                    errorCode = code
                }
            }
        ) {
            val user =
                telegramLinkService.linkAccount(
                    token = request.linkToken,
                    telegramId = request.telegramId,
                    telegramName = request.telegramName
                )
            linkTelegramAccountResponse {
                success = true
                displayName = user.displayName
                message = "Account linked successfully!"
            }
        }

    override fun unlinkTelegramAccount(
        request: UnlinkTelegramAccountRequest,
        responseObserver: StreamObserver<UnlinkTelegramAccountResponse>,
    ) = unary(responseObserver) { unlinkTelegramAccount(request) }

    internal fun unlinkTelegramAccount(request: UnlinkTelegramAccountRequest): UnlinkTelegramAccountResponse =
        grpcMutation(
            failure = { message, code ->
                unlinkTelegramAccountResponse {
                    success = false
                    this.message = message
                    errorCode = code
                }
            }
        ) {
            telegramLinkService.unlinkAccount(request.telegramId)
            unlinkTelegramAccountResponse {
                success = true
                message = "Account unlinked."
            }
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
                            user.id,
                            user.email,
                            PageRequest.of(pageNumber, GRPC_DEFAULT_PAGE_SIZE)
                        )
                    addAll(page.content)
                    pageNumber++
                } while (page.hasNext() && pageNumber < GRPC_MAX_PAGES)
            }
        return getMyVotesResponse {
            this.votes +=
                votes.map { dto ->
                    voteSummary {
                        voteId = dto.id.toString()
                        title = dto.title
                        status = dto.status.toProto()
                        mode = dto.mode.toProto()
                        participantCount = dto.participantCount.toInt()
                        isCreator = dto.isCreator
                        currentRound = dto.currentRound
                    }
                }
        }
    }

    override fun createVote(
        request: CreateVoteRequest,
        responseObserver: StreamObserver<CreateVoteResponse>,
    ) = unary(responseObserver) { createVote(request) }

    internal fun createVote(request: CreateVoteRequest): CreateVoteResponse {
        val user = linkedUser(request.telegramId)
        val mode = request.mode.toDomain()
        val participantEmails = request.participantEmailsList
        val options = request.optionsList

        return grpcMutation(
            failure = { message, code ->
                createVoteResponse {
                    success = false
                    this.message = message
                    errorCode = code
                }
            }
        ) {
            val vote =
                voteService.createVote(
                    creatorId = user.id,
                    request =
                        CreateVoteCommand(
                            title = request.title,
                            description = request.description.takeIf { it.isNotBlank() },
                            mode = mode,
                            participantEmails = participantEmails,
                            options = options,
                            drawTarget = request.drawTarget.toDomain()
                        )
                )

            createVoteResponse {
                success = true
                message = "Vote created"
                this.vote = vote.toProto()
            }
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
                voteService.getVote(voteId, user.id, user.email)
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
                drawVoteResponse {
                    success = false
                    this.message = message
                    errorCode = code
                }
            }
        ) {
            val result = voteService.draw(voteId, user.id)
            drawVoteResponse {
                success = true
                winnerEmail = result.winner.email
                winnerDisplayName = result.winner.displayName
                winnerOptionTitle = result.winner.optionTitle
                round = result.round
                newRoundStarted = result.newRoundStarted
                message = "✦ The Hand of Fate has chosen: ${result.winner.label}"
            }
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
                voteService.getLastResult(voteId, user.id, user.email)
            }

        return getLastDrawResultResponse {
            hasResult = lastDraw != null
            lastDraw?.let { result = it.toDrawResultInfo() }
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
                voteService.getHistory(voteId, user.id, user.email, historyPage(request))
            }
        return getVoteHistoryResponse {
            results += history.content.map { it.toDrawResultInfo() }
            totalPages = history.totalPages
            totalElements = history.totalElements
        }
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
