package com.juncevich.fate.vote.internal.port

import com.juncevich.fate.vote.internal.domain.VoteParticipant
import java.util.UUID

data class ParticipantCount(val voteId: UUID, val participantCount: Long)

interface ParticipantRepositoryPort {
    fun save(participant: VoteParticipant): VoteParticipant
    fun saveAll(participants: List<VoteParticipant>): List<VoteParticipant>
    fun findAllByVoteId(voteId: UUID): List<VoteParticipant>
    fun existsByVoteIdAndEmail(voteId: UUID, email: String): Boolean
    fun deleteByVoteIdAndEmail(voteId: UUID, email: String)
    fun countByVoteIds(voteIds: List<UUID>): List<ParticipantCount>
    fun findEligibleEmailsForRound(voteId: UUID, round: Int): List<String>
}
