package com.juncevich.fate.vote.internal.notification

import java.util.UUID

/**
 * Schema version of the persisted outbox payloads. Bump it on an incompatible change and keep a
 * reader for the previous version until its jobs have drained.
 */
const val NOTIFICATION_PAYLOAD_VERSION = 1

/*
 * Outbox payloads are a stored contract, deliberately decoupled from the domain events in
 * `VoteEvents.kt`: refactoring an event must not make already-queued jobs unreadable.
 * Each payload addresses exactly one recipient.
 */
sealed interface NotificationPayload {
    val version: Int
}

data class InvitationPayload(
    val voteId: UUID,
    val voteTitle: String,
    val creatorName: String,
    val recipientEmail: String,
    override val version: Int = NOTIFICATION_PAYLOAD_VERSION,
) : NotificationPayload

data class DrawResultPayload(
    val voteId: UUID,
    val voteTitle: String,
    val recipientEmail: String,
    val winnerName: String,
    val winnerEmail: String?,
    val round: Int,
    override val version: Int = NOTIFICATION_PAYLOAD_VERSION,
) : NotificationPayload
