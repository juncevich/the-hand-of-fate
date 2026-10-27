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
    val view = this
    return getVoteDetailsResponse {
        voteId = view.id.toString()
        title = view.title
        description = view.description.orEmpty()
        mode = view.mode.toProto()
        drawTarget = view.drawTarget.toProto()
        status = view.status.toProto()
        currentRound = view.currentRound
        participants +=
            view.participants.map {
                participantInfo {
                    email = it.email
                    displayName = it.displayName.orEmpty()
                }
            }
        options +=
            view.options.map {
                voteOptionInfo {
                    optionId = it.id.toString()
                    title = it.title
                }
            }
        view.lastResult?.let { lastResult = it.toDrawResultInfo() }
    }
}

internal fun DrawHistoryView.toDrawResultInfo(): DrawResultInfo {
    val view = this
    return drawResultInfo {
        winnerEmail = view.winner.email
        winnerDisplayName = view.winner.displayName
        winnerOptionTitle = view.winner.optionTitle
        round = view.round
        drawnAt = DateTimeFormatter.ISO_INSTANT.format(view.drawnAt)
    }
}

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
