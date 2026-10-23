package com.juncevich.fate.vote.persistence

import com.juncevich.fate.shared.uuidV7
import com.juncevich.fate.vote.DrawTarget
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import jakarta.persistence.*
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "votes")
@EntityListeners(AuditingEntityListener::class)
class VoteJpaEntity(
    @Id
    var id: UUID = uuidV7(),
    @Column(nullable = false, length = 255)
    var title: String,
    @Column(columnDefinition = "TEXT")
    var description: String? = null,
    @Column(name = "creator_id", nullable = false)
    var creatorId: UUID,
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(nullable = false, length = 20)
    var mode: VoteMode = VoteMode.SIMPLE,
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "draw_target", nullable = false, updatable = false, length = 20)
    var drawTarget: DrawTarget = DrawTarget.PARTICIPANTS,
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(nullable = false, length = 20)
    var status: VoteStatus = VoteStatus.PENDING,
    @Column(name = "current_round", nullable = false)
    var currentRound: Int = 1,
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Int = 0,
) {
    // Children are written through the vote (persist on save, delete when dropped from the list);
    // the database removes them with the vote (ON DELETE CASCADE). `var`: Hibernate replaces the
    // collection when loading, and writing a final field by reflection is being phased out (JEP 500).
    @Suppress("DoubleMutabilityForCollection")
    @OneToMany(mappedBy = "vote", cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
    var participants: MutableList<VoteParticipantJpaEntity> = mutableListOf()

    @Suppress("DoubleMutabilityForCollection")
    @OneToMany(mappedBy = "vote", cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
    @OrderBy("position ASC, createdAt ASC")
    var options: MutableList<VoteOptionJpaEntity> = mutableListOf()
}

@Entity
@Table(
    name = "vote_participants",
    uniqueConstraints = [UniqueConstraint(columnNames = ["vote_id", "email"])]
)
@EntityListeners(AuditingEntityListener::class)
class VoteParticipantJpaEntity(
    @Id
    var id: UUID = uuidV7(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_id", nullable = false)
    var vote: VoteJpaEntity,
    @Column(nullable = false, length = 255)
    var email: String,
    @Column(name = "display_name", length = 100)
    var displayName: String? = null,
    @CreatedDate
    @Column(name = "added_at", nullable = false, updatable = false)
    var addedAt: Instant = Instant.now(),
)

@Entity
@Table(
    name = "vote_options",
    uniqueConstraints = [UniqueConstraint(columnNames = ["vote_id", "title"])]
)
@EntityListeners(AuditingEntityListener::class)
class VoteOptionJpaEntity(
    @Id
    var id: UUID = uuidV7(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_id", nullable = false)
    var vote: VoteJpaEntity,
    @Column(nullable = false, length = 255)
    var title: String,
    @Column(nullable = false)
    var position: Int = 0,
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)

interface VoteJpaRepository : JpaRepository<VoteJpaEntity, UUID> {
    @Query(
        """
        SELECT DISTINCT v FROM VoteJpaEntity v
        WHERE v.creatorId = :userId
           OR EXISTS (
               SELECT 1 FROM VoteParticipantJpaEntity p WHERE p.vote = v AND p.email = :email
           )
        ORDER BY v.createdAt DESC
        """
    )
    fun findAllByUserIdOrParticipantEmail(
        userId: UUID,
        email: String,
        pageable: Pageable,
    ): Page<VoteJpaEntity>

    @Query(
        """
        SELECT p.vote.id AS voteId, COUNT(p) AS participantCount
        FROM VoteParticipantJpaEntity p
        WHERE p.vote.id IN :voteIds
        GROUP BY p.vote.id
        """
    )
    fun countParticipants(voteIds: Collection<UUID>): List<ParticipantCountProjection>

    fun countByCreatorId(creatorId: UUID): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VoteJpaEntity v WHERE v.id = :id")
    fun findByIdWithPessimisticLock(id: UUID): VoteJpaEntity?

    // One statement; the database deletes the children (ON DELETE CASCADE).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM VoteJpaEntity v WHERE v.id = :id")
    fun deleteVote(id: UUID)

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM VoteParticipantJpaEntity p WHERE p.email = :email")
    fun deleteParticipantsByEmail(email: String)
}

interface ParticipantCountProjection {
    val voteId: UUID
    val participantCount: Long
}
