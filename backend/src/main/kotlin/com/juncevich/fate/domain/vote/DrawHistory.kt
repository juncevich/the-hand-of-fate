package com.juncevich.fate.domain.vote

import jakarta.persistence.*
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "draw_history")
class DrawHistory(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @get:JvmName("getId_")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_id", nullable = false)
    val vote: Vote,

    @Column(name = "winner_email", nullable = false, length = 255)
    val winnerEmail: String,

    @Column(name = "winner_display_name", length = 100)
    val winnerDisplayName: String? = null,

    @Column(nullable = false)
    val round: Int,

    @Column(name = "drawn_at", nullable = false)
    val drawnAt: Instant = Instant.now(),
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
