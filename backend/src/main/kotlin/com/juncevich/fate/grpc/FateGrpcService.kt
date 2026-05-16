package com.juncevich.fate.grpc

import com.juncevich.fate.auth.TelegramLinkService
import com.juncevich.fate.auth.UserRepository
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

@GrpcService
class FateGrpcService(
    private val userRepository: UserRepository,
    private val voteRepository: VoteRepository,
    private val participantRepository: VoteParticipantRepository,
    private val drawHistoryRepository: DrawHistoryRepository,
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
        val page = voteService.listVotes(user.id, user.email, PageRequest.of(0, 20))
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
                        com.juncevich.fate.vote.CreateVoteRequest(
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
            CreateVoteResponse
                .newBuilder()
                .setSuccess(false)
                .setMessage(ex.message ?: "Vote creation failed")
                .build()
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
            DrawVoteResponse
                .newBuilder()
                .setSuccess(false)
                .setMessage(ex.message ?: "Draw failed")
                .build()
        }
    }

    override suspend fun getLastDrawResult(request: GetLastDrawResultRequest): GetLastDrawResultResponse {
        val user = linkedUser(request.telegramId)
        val voteId = parseVoteId(request.voteId)
        val vote =
            voteRepository
                .findById(voteId)
                .orElseThrow { StatusRuntimeException(Status.NOT_FOUND.withDescription("Vote not found")) }
        requireVoteAccess(vote.id, vote.creator.id, user.id, user.email)

        val lastDraw = drawHistoryRepository.findTopByVoteIdOrderByDrawnAtDesc(voteId)
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
            builder.setLastResult(
                DrawResultInfo
                    .newBuilder()
                    .setWinnerEmail(last.winnerEmail ?: "")
                    .setWinnerDisplayName(last.winnerDisplayName ?: "")
                    .setWinnerOptionTitle(last.winnerOptionTitle ?: "")
                    .setRound(last.round)
                    .setDrawnAt(DateTimeFormatter.ISO_INSTANT.format(last.drawnAt))
                    .build()
            )
        }
        return builder.build()
    }

    private fun DrawHistory.toDrawResultInfo(): DrawResultInfo =
        DrawResultInfo
            .newBuilder()
            .setWinnerEmail(winnerEmail ?: "")
            .setWinnerDisplayName(winnerDisplayName ?: "")
            .setWinnerOptionTitle(winnerOptionTitle ?: "")
            .setRound(round)
            .setDrawnAt(DateTimeFormatter.ISO_INSTANT.format(drawnAt))
            .build()

    private fun linkedUser(telegramId: Long) =
        userRepository.findByTelegramId(telegramId)
            ?: throw StatusRuntimeException(Status.NOT_FOUND.withDescription("Telegram account not linked"))

    private fun parseVoteId(value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse { throw StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Invalid vote id")) }

    private fun requireVoteAccess(
        voteId: UUID,
        creatorId: UUID,
        userId: UUID,
        email: String,
    ) {
        val hasAccess = creatorId == userId || participantRepository.existsByVoteIdAndEmail(voteId, email)
        if (!hasAccess) {
            throw StatusRuntimeException(
                Status.PERMISSION_DENIED.withDescription("Vote is not available for this user")
            )
        }
    }

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
