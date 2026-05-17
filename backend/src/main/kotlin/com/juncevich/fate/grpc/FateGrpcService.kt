package com.juncevich.fate.grpc

import com.juncevich.fate.auth.TelegramLinkService
import com.juncevich.fate.auth.UserQueryService
import com.juncevich.fate.grpc.FateProto.*
import com.juncevich.fate.vote.*
import io.grpc.Status
import io.grpc.StatusRuntimeException
import net.devh.boot.grpc.server.service.GrpcService
import org.springframework.data.domain.PageRequest
import java.time.format.DateTimeFormatter
import java.util.UUID
import com.juncevich.fate.vote.VoteMode as DomainVoteMode
import com.juncevich.fate.vote.VoteStatus as DomainVoteStatus

private const val GRPC_DEFAULT_PAGE_SIZE = 20

@GrpcService
class FateGrpcService(
    private val userQueryService: UserQueryService,
    private val telegramLinkService: TelegramLinkService,
    private val voteService: VoteService,
) : FateServiceGrpcKt.FateServiceCoroutineImplBase() {
    override suspend fun linkTelegramAccount(request: LinkTelegramAccountRequest): LinkTelegramAccountResponse =
        runCatching {
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
        }.getOrElse { ex ->
            LinkTelegramAccountResponse
                .newBuilder()
                .setSuccess(false)
                .setMessage(ex.message ?: "Failed to link account")
                .build()
        }

    override suspend fun unlinkTelegramAccount(request: UnlinkTelegramAccountRequest): UnlinkTelegramAccountResponse =
        runCatching {
            telegramLinkService.unlinkAccount(request.telegramId)
            UnlinkTelegramAccountResponse
                .newBuilder()
                .setSuccess(true)
                .setMessage("Account unlinked.")
                .build()
        }.getOrElse { ex ->
            UnlinkTelegramAccountResponse
                .newBuilder()
                .setSuccess(false)
                .setMessage(ex.message ?: "Failed to unlink")
                .build()
        }

    override suspend fun getMyVotes(request: GetMyVotesRequest): GetMyVotesResponse {
        val user = linkedUser(request.telegramId)
        val page = voteService.listVotes(user.id, user.email, PageRequest.of(0, GRPC_DEFAULT_PAGE_SIZE))
        val summaries =
            page.content.map { dto ->
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
        val participantEmails =
            request.participantEmailsList
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        val options =
            request.optionsList
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        return runCatching {
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
        }.getOrElse { ex ->
            when (ex) {
                is NoSuchElementException, is IllegalStateException, is IllegalArgumentException ->
                    CreateVoteResponse.newBuilder().setSuccess(false).setMessage(ex.message ?: "Vote creation failed").build()
                else -> throw StatusRuntimeException(Status.INTERNAL.withDescription("Unexpected error"))
            }
        }
    }

    override suspend fun getVoteDetails(request: GetVoteDetailsRequest): GetVoteDetailsResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)
        val voteDto =
            runCatching {
                voteService.getVote(voteId, user.id, user.email)
            }.getOrElse { ex ->
                when (ex) {
                    is NoSuchElementException -> throw StatusRuntimeException(
                        Status.NOT_FOUND.withDescription(ex.message)
                    )

                    is IllegalStateException -> throw StatusRuntimeException(
                        Status.PERMISSION_DENIED.withDescription(ex.message)
                    )

                    else -> throw StatusRuntimeException(Status.INTERNAL.withDescription("Unexpected error"))
                }
            }
        return buildVoteDetailsResponse(voteDto)
    }

    override suspend fun drawVote(request: DrawVoteRequest): DrawVoteResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)
        return runCatching {
            val result = voteService.draw(voteId, user.id)
            DrawVoteResponse
                .newBuilder()
                .setSuccess(true)
                .setWinnerEmail(result.winnerEmail ?: "")
                .setWinnerDisplayName(result.winnerDisplayName ?: "")
                .setWinnerOptionTitle(result.winnerOptionTitle ?: "")
                .setRound(result.round)
                .setNewRoundStarted(result.newRoundStarted)
                .setMessage("✦ The Hand of Fate has chosen: ${result.winnerLabel}")
                .build()
        }.getOrElse { ex ->
            when (ex) {
                is NoSuchElementException, is IllegalStateException, is IllegalArgumentException ->
                    DrawVoteResponse.newBuilder().setSuccess(false).setMessage(ex.message ?: "Draw failed").build()
                else -> throw StatusRuntimeException(Status.INTERNAL.withDescription("Unexpected error"))
            }
        }
    }

    override suspend fun getLastDrawResult(request: GetLastDrawResultRequest): GetLastDrawResultResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)

        val lastDraw =
            runCatching {
                voteService.getLastResult(voteId, user.id, user.email)
            }.getOrElse { ex ->
                when (ex) {
                    is NoSuchElementException -> throw StatusRuntimeException(
                        Status.NOT_FOUND.withDescription(ex.message)
                    )

                    is IllegalStateException -> throw StatusRuntimeException(
                        Status.PERMISSION_DENIED.withDescription(ex.message)
                    )

                    else -> throw StatusRuntimeException(Status.INTERNAL.withDescription("Unexpected error"))
                }
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
            runCatching {
                voteService.getHistory(voteId, user.id, user.email)
            }.getOrElse { ex ->
                when (ex) {
                    is NoSuchElementException -> throw StatusRuntimeException(
                        Status.NOT_FOUND.withDescription(ex.message)
                    )

                    is IllegalStateException -> throw StatusRuntimeException(
                        Status.PERMISSION_DENIED.withDescription(ex.message)
                    )

                    else -> throw StatusRuntimeException(Status.INTERNAL.withDescription("Unexpected error"))
                }
            }
        return GetVoteHistoryResponse.newBuilder().addAllResults(history.map { it.toDrawResultInfo() }).build()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildVoteDetailsResponse(vote: VoteDetailDto): GetVoteDetailsResponse {
        val builder =
            GetVoteDetailsResponse
                .newBuilder()
                .setVoteId(vote.id.toString())
                .setTitle(vote.title)
                .setDescription(vote.description ?: "")
                .setMode(vote.mode.toProto())
                .setStatus(vote.status.toProto())
                .setCurrentRound(vote.currentRound)
                .addAllParticipants(
                    vote.participants.map {
                        ParticipantInfo
                            .newBuilder()
                            .setEmail(it.email)
                            .setDisplayName(it.displayName ?: "")
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
            .setWinnerEmail(winnerEmail ?: "")
            .setWinnerDisplayName(winnerDisplayName ?: "")
            .setWinnerOptionTitle(winnerOptionTitle ?: "")
            .setRound(round)
            .setDrawnAt(DateTimeFormatter.ISO_INSTANT.format(drawnAt))
            .build()

    private fun linkedUser(telegramId: Long) =
        userQueryService.findByTelegramId(telegramId)
            ?: throw StatusRuntimeException(Status.NOT_FOUND.withDescription("Telegram account not linked"))

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
