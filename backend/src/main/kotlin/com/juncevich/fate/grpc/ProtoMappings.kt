package com.juncevich.fate.grpc

import com.juncevich.fate.vote.DrawHistoryView
import com.juncevich.fate.vote.VoteDetailView
import com.juncevich.fate.vote.Winner
import java.time.format.DateTimeFormatter
import com.juncevich.fate.vote.DrawTarget as DomainDrawTarget
import com.juncevich.fate.vote.VoteMode as DomainVoteMode
import com.juncevich.fate.vote.VoteStatus as DomainVoteStatus

// Module read models → protobuf messages of `fate.v1`, and the enums both ways.

internal fun VoteDetailView.toProto(): GetVoteDetailsResponse {
    val builder =
        GetVoteDetailsResponse
            .newBuilder()
            .setVoteId(id.toString())
            .setTitle(title)
            .setDescription(description.orEmpty())
            .setMode(mode.toProto())
            .setDrawTarget(drawTarget.toProto())
            .setStatus(status.toProto())
            .setCurrentRound(currentRound)
            .addAllParticipants(
                participants.map {
                    ParticipantInfo
                        .newBuilder()
                        .setEmail(it.email)
                        .setDisplayName(it.displayName.orEmpty())
                        .build()
                }
            ).addAllOptions(
                options.map {
                    VoteOptionInfo
                        .newBuilder()
                        .setOptionId(it.id.toString())
                        .setTitle(it.title)
                        .build()
                }
            )
    lastResult?.let { last ->
        builder.setLastResult(last.toDrawResultInfo())
    }
    return builder.build()
}

internal fun DrawHistoryView.toDrawResultInfo(): DrawResultInfo =
    DrawResultInfo
        .newBuilder()
        .setWinnerEmail(winner.email)
        .setWinnerDisplayName(winner.displayName)
        .setWinnerOptionTitle(winner.optionTitle)
        .setRound(round)
        .setDrawnAt(DateTimeFormatter.ISO_INSTANT.format(drawnAt))
        .build()

// proto3 strings have no null: the fields of the other kind of winner are empty.
internal val Winner.email get() = (this as? Winner.Participant)?.email.orEmpty()
internal val Winner.displayName get() = (this as? Winner.Participant)?.displayName.orEmpty()
internal val Winner.optionTitle get() = (this as? Winner.Option)?.title.orEmpty()

// ── Proto enum conversions ──────────────────────────────────────────────────

internal fun DomainVoteStatus.toProto(): VoteStatus =
    when (this) {
        DomainVoteStatus.PENDING -> VoteStatus.VOTE_STATUS_PENDING
        DomainVoteStatus.DRAWN -> VoteStatus.VOTE_STATUS_DRAWN
        DomainVoteStatus.CLOSED -> VoteStatus.VOTE_STATUS_CLOSED
    }

internal fun DomainVoteMode.toProto(): VoteMode =
    when (this) {
        DomainVoteMode.SIMPLE -> VoteMode.VOTE_MODE_SIMPLE
        DomainVoteMode.FAIR_ROTATION -> VoteMode.VOTE_MODE_FAIR_ROTATION
    }

internal fun DomainDrawTarget.toProto(): DrawTarget =
    when (this) {
        DomainDrawTarget.PARTICIPANTS -> DrawTarget.DRAW_TARGET_PARTICIPANTS
        DomainDrawTarget.OPTIONS -> DrawTarget.DRAW_TARGET_OPTIONS
    }

internal fun DrawTarget.toDomain(): DomainDrawTarget? =
    when (this) {
        DrawTarget.DRAW_TARGET_PARTICIPANTS -> DomainDrawTarget.PARTICIPANTS
        DrawTarget.DRAW_TARGET_OPTIONS -> DomainDrawTarget.OPTIONS
        DrawTarget.DRAW_TARGET_UNSPECIFIED, DrawTarget.UNRECOGNIZED -> null
    }

internal fun VoteMode.toDomain(): DomainVoteMode =
    when (this) {
        VoteMode.VOTE_MODE_FAIR_ROTATION -> DomainVoteMode.FAIR_ROTATION

        VoteMode.VOTE_MODE_SIMPLE,
        VoteMode.VOTE_MODE_UNSPECIFIED,
        VoteMode.UNRECOGNIZED,
        -> DomainVoteMode.SIMPLE
    }
