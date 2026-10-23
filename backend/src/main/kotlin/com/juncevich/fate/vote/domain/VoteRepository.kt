package com.juncevich.fate.vote.domain

import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

/** Loads and stores [Vote] aggregates whole: the vote with its participants and options. */
interface VoteRepository {
    fun find(id: UUID): Vote?

    /** Like [find], holding the vote's row lock until the transaction ends; every change loads it this way. */
    fun findForUpdate(id: UUID): Vote?

    /** Inserts a new vote or writes a loaded one back; fails if the stored vote changed since [vote] was loaded. */
    fun save(vote: Vote)

    fun delete(id: UUID)

    /** Votes [userId] created or [email] participates in, newest first. */
    fun findSummaries(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<VoteSummary>

    fun countByCreator(creatorId: UUID): Long

    /** Removes [email] from the participants of every vote. */
    fun removeParticipantEverywhere(email: String)
}

/** A list entry: the vote without its participants and options. */
data class VoteSummary(
    val id: UUID,
    val title: String,
    val creatorId: UUID,
    val mode: VoteMode,
    val status: VoteStatus,
    val currentRound: Int,
    val participantCount: Long,
    val createdAt: Instant,
)

/** The append-only log of draws; not part of [Vote], it grows with every draw. */
interface DrawHistoryRepository {
    fun save(entry: DrawHistory)

    fun findLatest(voteId: UUID): DrawHistory?

    /** Newest first; equal timestamps in a stable order. */
    fun findPage(
        voteId: UUID,
        pageable: Pageable,
    ): Page<DrawHistory>

    fun findByRound(
        voteId: UUID,
        round: Int,
    ): List<DrawHistory>

    /** Keeps [email]'s past wins under [displayName] and without the address. */
    fun anonymizeWins(
        email: String,
        displayName: String,
    )
}
