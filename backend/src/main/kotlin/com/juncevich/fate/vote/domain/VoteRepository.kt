package com.juncevich.fate.vote.domain

import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.vote.VoteId
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import java.time.Instant
import java.util.UUID

/** Loads and stores [Vote] aggregates whole: the vote with its participants and options. */
interface VoteRepository {
    fun find(id: VoteId): Vote?

    /** Like [find], holding the vote's row lock until the transaction ends; every change loads it this way. */
    fun findForUpdate(id: VoteId): Vote?

    /** Inserts a new vote or writes a loaded one back; fails if the stored vote changed since [vote] was loaded. */
    fun save(vote: Vote)

    fun delete(id: VoteId)

    /** Votes [userId] created or [email] participates in, newest first. */
    fun findSummaries(
        userId: UserId,
        email: Email,
        page: PageQuery,
    ): PageResult<VoteSummary>

    fun countByCreator(creatorId: UserId): Long

    /** Removes [email] from the participants of every vote. */
    fun removeParticipantEverywhere(email: Email)
}

/** A list entry: the vote without its participants and options. */
data class VoteSummary(
    val id: VoteId,
    val title: String,
    val creatorId: UserId,
    val mode: VoteMode,
    val status: VoteStatus,
    val currentRound: Int,
    val participantCount: Long,
    val createdAt: Instant,
)

/** The append-only log of draws; not part of [Vote], it grows with every draw. */
interface DrawHistoryRepository {
    fun save(entry: DrawHistory)

    fun findLatest(voteId: VoteId): DrawHistory?

    /** Newest first; equal timestamps in a stable order. */
    fun findPage(
        voteId: VoteId,
        page: PageQuery,
    ): PageResult<DrawHistory>

    fun findByRound(
        voteId: VoteId,
        round: Int,
    ): List<DrawHistory>

    /** Keeps [email]'s past wins under [displayName] and without the address. */
    fun anonymizeWins(
        email: Email,
        displayName: String,
    )
}
