package com.juncevich.fate.account.persistence

import com.juncevich.fate.shared.uuidV7
import jakarta.persistence.*
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

// One constructor parameter per column of `users`; grouping them would only obscure the mapping.
@Suppress("LongParameterList")
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener::class)
class UserJpaEntity(
    @Id
    var id: UUID = uuidV7(),
    @Column(nullable = false, unique = true, length = 255)
    var email: String,
    @Column(name = "display_name", nullable = false, length = 100)
    var displayName: String,
    @Column(name = "telegram_id", unique = true)
    var telegramId: Long? = null,
    @Column(name = "telegram_name", length = 100)
    var telegramName: String? = null,
    @Column(name = "avatar_updated_at")
    var avatarUpdatedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
    /** Technical: set by Spring Data auditing on every write (from the application `Clock`). */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)

interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByEmail(email: String): UserJpaEntity?

    fun findByTelegramId(telegramId: Long): UserJpaEntity?

    fun findAllByEmailIn(emails: Collection<String>): List<UserJpaEntity>

    // Native: JPA cannot change an id. References follow via ON UPDATE CASCADE (V25).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE users SET id = :to, updated_at = now() WHERE id = :from", nativeQuery = true)
    fun changeId(
        from: UUID,
        to: UUID,
    ): Int

    // Concurrent first requests of a new account race here; the loser inserts nothing.
    @Modifying(clearAutomatically = true)
    @Query(
        """
        INSERT INTO users (id, email, display_name, created_at, updated_at)
        VALUES (:id, :email, :displayName, :createdAt, :createdAt)
        ON CONFLICT DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        id: UUID,
        email: String,
        displayName: String,
        createdAt: Instant,
    ): Int
}
