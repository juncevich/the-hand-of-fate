package com.juncevich.fate.grpc

import com.juncevich.fate.domain.user.UserRepository
import com.juncevich.fate.domain.vote.VoteMode as DomainVoteMode
import com.juncevich.fate.domain.vote.VoteStatus as DomainVoteStatus
import com.juncevich.fate.grpc.FateProto.*
import com.juncevich.fate.service.DrawService
import com.juncevich.fate.service.TelegramLinkService
import com.juncevich.fate.domain.vote.VoteRepository
import com.juncevich.fate.domain.vote.VoteParticipantRepository
import com.juncevich.fate.domain.vote.DrawHistoryRepository
import io.grpc.Status
import io.grpc.StatusRuntimeException
import net.devh.boot.grpc.server.service.GrpcService
import org.springframework.data.domain.PageRequest
import java.time.format.DateTimeFormatter
import java.util.UUID

@GrpcService
class FateGrpcService(
    private val userRepository: UserRepository,
    private val voteRepository: VoteRepository,
    private val participantRepository: VoteParticipantRepository,
    private val drawHistoryRepository: DrawHistoryRepository,
    private val telegramLinkService: TelegramLinkService,
    private val drawService: DrawService,
) : FateServiceGrpcKt.FateServiceCoroutineImplBase() {

    override suspend fun linkTelegramAccount(
        request: LinkTelegramAccountRequest,
    ): LinkTelegramAccountResponse {
        return runCatching {
            val user = telegramLinkService.linkAccount(
                token = request.linkToken,
                telegramId = request.telegramId,
                telegramName = request.telegramName,
            )
            LinkTelegramAccountResponse.newBuilder()
                .setSuccess(true)
                .setDisplayName(user.displayName)
                .setMessage("Account linked successfully!")
                .build()
        }.getOrElse { ex ->
            LinkTelegramAccountResponse.newBuilder()
                .setSuccess(false)
                .setMessage(ex.message ?: "Failed to link account")
                .build()
        }
    }

    override suspend fun unlinkTelegramAccount(
        request: UnlinkTelegramAccountRequest,
    ): UnlinkTelegramAccountResponse {
        return runCatching {
            telegramLinkService.unlinkAccount(request.telegramId)
            UnlinkTelegramAccountResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Account unlinked.")
                .build()
        }.getOrElse { ex ->
            UnlinkTelegramAccountResponse.newBuilder()
                .setSuccess(false)
                .setMessage(ex.message ?: "Failed to unlink")
                .build()
        }
    }

    override suspend fun getMyVotes(request: GetMyVotesRequest): GetMyVotesResponse {
        val user = userRepository.findByTelegramId(request.telegramId)
            ?: throw StatusRuntimeException(
                Status.NOT_FOUND.withDescription("Telegram account not linked. Use /link <token> first.")
            )

        val votes = voteRepository.findAllByUserIdOrParticipantEmail(
            userId = user.id,
            email = user.email,
            pageable = PageRequest.of(0, 20),
        )

        val summaries = votes.content.map { vote ->
            val count = participantRepository.countByVoteId(vote.id)
            VoteSummary.newBuilder()
                .setVoteId(vote.id.toString())
                .setTitle(vote.title)
                .setStatus(vote.status.toProto())
                .setMode(vote.mode.toProto())
                .setParticipantCount(count.toInt())
                .setIsCreator(vote.creator.id == user.id)
                .setCurrentRound(vote.currentRound)
                .build()
        }

        return GetMyVotesResponse.newBuilder().addAllVotes(summaries).build()
    }

    override suspend fun getVoteDetails(request: GetVoteDetailsRequest): GetVoteDetailsResponse {
        val user = userRepository.findByTelegramId(request.telegramId)
            ?: throw StatusRuntimeException(Status.NOT_FOUND.withDescription("Telegram account not linked"))

        val vote = voteRepository.findById(UUID.fromString(request.voteId))
            .orElseThrow { StatusRuntimeException(Status.NOT_FOUND.withDescription("Vote not found")) }

        val participants = participantRepository.findAllByVoteId(vote.id).map { p ->
            ParticipantInfo.newBuilder()
                .setEmail(p.email)
                .setDisplayName(p.displayName ?: "")
                .build()
        }

        val lastDraw = drawHistoryRepository.findTopByVoteIdOrderByDrawnAtDesc(vote.id)
        val builder = GetVoteDetailsResponse.newBuilder()
            .setVoteId(vote.id.toString())
            .setTitle(vote.title)
            .setDescription(vote.description ?: "")
            .setMode(vote.mode.toProto())
            .setStatus(vote.status.toProto())
            .setCurrentRound(vote.currentRound)
            .addAllParticipants(participants)

        lastDraw?.let {
            builder.setLastResult(
                DrawResultInfo.newBuilder()
                    .setWinnerEmail(it.winnerEmail)
                    .setWinnerDisplayName(it.winnerDisplayName ?: "")
                    .setRound(it.round)
                    .setDrawnAt(DateTimeFormatter.ISO_INSTANT.format(it.drawnAt))
                    .build()
            )
        }

        return builder.build()
    }

    override suspend fun drawVote(request: DrawVoteRequest): DrawVoteResponse {
        val user = userRepository.findByTelegramId(request.telegramId)
            ?: throw StatusRuntimeException(Status.NOT_FOUND.withDescription("Telegram account not linked"))

        val voteId = UUID.fromString(request.voteId)
        val vote = voteRepository.findById(voteId)
            .orElseThrow { StatusRuntimeException(Status.NOT_FOUND.withDescription("Vote not found")) }

        if (vote.creator.id != user.id) {
            throw StatusRuntimeException(Status.PERMISSION_DENIED.withDescription("Only the vote creator can draw"))
        }

        return runCatching {
            val result = drawService.draw(voteId)
            DrawVoteResponse.newBuilder()
                .setSuccess(true)
                .setWinnerEmail(result.winnerEmail)
                .setWinnerDisplayName(result.winnerDisplayName ?: "")
                .setRound(result.round)
                .setNewRoundStarted(result.newRoundStarted)
                .setMessage("✦ The Hand of Fate has chosen: ${result.winnerDisplayName ?: result.winnerEmail}")
                .build()
        }.getOrElse { ex ->
            DrawVoteResponse.newBuilder()
                .setSuccess(false)
                .setMessage(ex.message ?: "Draw failed")
                .build()
        }
    }

    override suspend fun getLastDrawResult(request: GetLastDrawResultRequest): GetLastDrawResultResponse {
        val voteId = UUID.fromString(request.voteId)
        val lastDraw = drawHistoryRepository.findTopByVoteIdOrderByDrawnAtDesc(voteId)

        return if (lastDraw == null) {
            GetLastDrawResultResponse.newBuilder().setHasResult(false).build()
        } else {
            GetLastDrawResultResponse.newBuilder()
                .setHasResult(true)
                .setResult(
                    DrawResultInfo.newBuilder()
                        .setWinnerEmail(lastDraw.winnerEmail)
                        .setWinnerDisplayName(lastDraw.winnerDisplayName ?: "")
                        .setRound(lastDraw.round)
                        .setDrawnAt(DateTimeFormatter.ISO_INSTANT.format(lastDraw.drawnAt))
                        .build()
                )
                .build()
        }
    }

    // ── Proto enum conversions ──────────────────────────────────────────────

    private fun DomainVoteStatus.toProto(): VoteStatus = when (this) {
        DomainVoteStatus.PENDING -> VoteStatus.VOTE_STATUS_PENDING
        DomainVoteStatus.DRAWN   -> VoteStatus.VOTE_STATUS_DRAWN
        DomainVoteStatus.CLOSED  -> VoteStatus.VOTE_STATUS_CLOSED
    }

    private fun DomainVoteMode.toProto(): VoteMode = when (this) {
        DomainVoteMode.SIMPLE        -> VoteMode.VOTE_MODE_SIMPLE
        DomainVoteMode.FAIR_ROTATION -> VoteMode.VOTE_MODE_FAIR_ROTATION
    }
}
