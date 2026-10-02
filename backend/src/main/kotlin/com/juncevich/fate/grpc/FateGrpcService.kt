package com.juncevich.fate.grpc

import com.juncevich.fate.auth.TelegramLinkService
import com.juncevich.fate.auth.UserQueryService
import com.juncevich.fate.vote.*
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.springframework.data.domain.PageRequest
import org.springframework.grpc.server.service.GrpcService
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors
import com.juncevich.fate.vote.VoteMode as DomainVoteMode
import com.juncevich.fate.vote.VoteStatus as DomainVoteStatus

private const val GRPC_DEFAULT_PAGE_SIZE = 50

// Upper bound on pages fetched for a single bot listing, so a user with an
// unusually large number of votes can't force an unbounded aggregation.
private const val GRPC_MAX_PAGES = 20

// Every RPC body makes blocking JPA calls; a virtual thread per dispatch parks cheaply
// while waiting on the database instead of holding a platform thread.
private val virtualThreadDispatcher: CoroutineDispatcher =
    Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()

@GrpcService
class FateGrpcService(
    private val userQueryService: UserQueryService,
    private val telegramLinkService: TelegramLinkService,
    private val voteService: VoteService,
    // Coroutine context for every RPC; injectable so tests can swap it
    dispatcher: CoroutineDispatcher = virtualThreadDispatcher,
) : FateServiceGrpcKt.FateServiceCoroutineImplBase(dispatcher) {
    override suspend fun linkTelegramAccount(request: LinkTelegramAccountRequest): LinkTelegramAccountResponse =
        grpcMutation(
            failure = {
                LinkTelegramAccountResponse
                    .newBuilder()
                    .setSuccess(false)
                    .setMessage(it)
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

    override suspend fun unlinkTelegramAccount(request: UnlinkTelegramAccountRequest): UnlinkTelegramAccountResponse =
        grpcMutation(
            failure = {
                UnlinkTelegramAccountResponse
                    .newBuilder()
                    .setSuccess(false)
                    .setMessage(it)
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

    override suspend fun getMyVotes(request: GetMyVotesRequest): GetMyVotesResponse {
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

    override suspend fun createVote(request: CreateVoteRequest): CreateVoteResponse {
        val user = linkedUser(request.telegramId)
        val title = request.title.trim()
        if (title.isBlank()) {
            throw StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Vote title is required"))
        }
        val mode = request.mode.toDomain()
        val participantEmails = request.participantEmailsList
        val options = request.optionsList

        return grpcMutation(
            failure = {
                CreateVoteResponse
                    .newBuilder()
                    .setSuccess(false)
                    .setMessage(it)
                    .build()
            }
        ) {
            val vote =
                voteService.createVote(
                    creatorId = user.id,
                    request =
                        CreateVoteCommand(
                            title = title,
                            description = request.description.takeIf { it.isNotBlank() },
                            mode = mode,
                            participantEmails = participantEmails,
                            options = options
                        )
                )

            CreateVoteResponse
                .newBuilder()
                .setSuccess(true)
                .setMessage("Vote created")
                .setVote(buildVoteDetailsResponse(vote))
                .build()
        }
    }

    override suspend fun getVoteDetails(request: GetVoteDetailsRequest): GetVoteDetailsResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)
        val voteDto =
            grpcRead {
                voteService.getVote(voteId, user.id, user.email)
            }
        return buildVoteDetailsResponse(voteDto)
    }

    override suspend fun drawVote(request: DrawVoteRequest): DrawVoteResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)
        return grpcMutation(
            failure = {
                DrawVoteResponse
                    .newBuilder()
                    .setSuccess(false)
                    .setMessage(it)
                    .build()
            }
        ) {
            val result = voteService.draw(voteId, user.id)
            DrawVoteResponse
                .newBuilder()
                .setSuccess(true)
                .setWinnerEmail(result.winnerEmail.orEmpty())
                .setWinnerDisplayName(result.winnerDisplayName.orEmpty())
                .setWinnerOptionTitle(result.winnerOptionTitle.orEmpty())
                .setRound(result.round)
                .setNewRoundStarted(result.newRoundStarted)
                .setMessage("✦ The Hand of Fate has chosen: ${result.winnerLabel}")
                .build()
        }
    }

    override suspend fun getLastDrawResult(request: GetLastDrawResultRequest): GetLastDrawResultResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)

        val lastDraw =
            grpcRead {
                voteService.getLastResult(voteId, user.id, user.email)
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

    override suspend fun getVoteHistory(request: GetVoteHistoryRequest): GetVoteHistoryResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)
        val history =
            grpcRead {
                voteService.getHistory(voteId, user.id, user.email, historyPage(request))
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
            throw StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Invalid history pagination"))
        }
        return PageRequest.of(request.page, size)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildVoteDetailsResponse(vote: VoteDetailDto): GetVoteDetailsResponse {
        val builder =
            GetVoteDetailsResponse
                .newBuilder()
                .setVoteId(vote.id.toString())
                .setTitle(vote.title)
                .setDescription(vote.description.orEmpty())
                .setMode(vote.mode.toProto())
                .setStatus(vote.status.toProto())
                .setCurrentRound(vote.currentRound)
                .addAllParticipants(
                    vote.participants.map {
                        ParticipantInfo
                            .newBuilder()
                            .setEmail(it.email)
                            .setDisplayName(it.displayName.orEmpty())
                            .build()
                    }
                ).addAllOptions(
                    vote.options.map {
                        VoteOptionInfo
                            .newBuilder()
                            .setOptionId(it.id.toString())
                            .setTitle(it.title)
                            .build()
                    }
                )
        vote.lastResult?.let { last ->
            builder.setLastResult(last.toDrawResultInfo())
        }
        return builder.build()
    }

    private fun DrawHistoryDto.toDrawResultInfo(): DrawResultInfo =
        DrawResultInfo
            .newBuilder()
            .setWinnerEmail(winnerEmail.orEmpty())
            .setWinnerDisplayName(winnerDisplayName.orEmpty())
            .setWinnerOptionTitle(winnerOptionTitle.orEmpty())
            .setRound(round)
            .setDrawnAt(DateTimeFormatter.ISO_INSTANT.format(drawnAt))
            .build()

    private fun linkedUser(telegramId: Long) =
        userQueryService.findByTelegramId(telegramId)
            ?: throw GrpcErrors.telegramNotLinked()

    private fun parseVoteId(value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse { throw StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Invalid vote id")) }

    // ── Proto enum conversions ──────────────────────────────────────────────

    private fun DomainVoteStatus.toProto(): VoteStatus =
        when (this) {
            DomainVoteStatus.PENDING -> VoteStatus.VOTE_STATUS_PENDING
            DomainVoteStatus.DRAWN -> VoteStatus.VOTE_STATUS_DRAWN
            DomainVoteStatus.CLOSED -> VoteStatus.VOTE_STATUS_CLOSED
        }

    private fun DomainVoteMode.toProto(): VoteMode =
        when (this) {
            DomainVoteMode.SIMPLE -> VoteMode.VOTE_MODE_SIMPLE
            DomainVoteMode.FAIR_ROTATION -> VoteMode.VOTE_MODE_FAIR_ROTATION
        }

    private fun VoteMode.toDomain(): DomainVoteMode =
        when (this) {
            VoteMode.VOTE_MODE_FAIR_ROTATION -> DomainVoteMode.FAIR_ROTATION

            VoteMode.VOTE_MODE_SIMPLE,
            VoteMode.VOTE_MODE_UNSPECIFIED,
            VoteMode.UNRECOGNIZED,
            -> DomainVoteMode.SIMPLE
        }
}
