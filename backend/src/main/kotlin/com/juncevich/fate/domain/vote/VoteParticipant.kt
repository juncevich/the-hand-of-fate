package com.juncevich.fate.domain.vote

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.domain.Persistable
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "vote_participants",
    uniqueConstraints = [UniqueConstraint(columnNames = ["vote_id", "email"])]
)
@EntityListeners(AuditingEntityListener::class)
class VoteParticipant(

    @Id
    @get:JvmName("getId_")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_id", nullable = false)
    val vote: Vote,

    // Denormalized email — participant may not be registered yet at time of invite
    @Column(nullable = false, length = 255)
    val email: String,

    @Column(name = "display_name", length = 100)
    var displayName: String? = null,

    @CreatedDate
    @Column(name = "added_at", nullable = false, updatable = false)
    var addedAt: Instant = Instant.now(),
) : Persistable<UUID> {

    @Transient
    private var _isNew: Boolean = true

    override fun getId(): UUID = id
    override fun isNew(): Boolean = _isNew

    @PostPersist
    @PostLoad
    fun markNotNew() {
        _isNew = false
    }
}
