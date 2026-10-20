package com.juncevich.fate.auth.internal.persistence.jpa

import com.juncevich.fate.auth.internal.persistence.entity.UserJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByEmail(email: String): UserJpaEntity?

    fun findByTelegramId(telegramId: Long): UserJpaEntity?

    fun existsByEmail(email: String): Boolean

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
        INSERT INTO users (id, email, password_hash, display_name, email_verified_at, created_at, updated_at)
        VALUES (:id, :email, :passwordHash, :displayName, :emailVerifiedAt, :createdAt, :createdAt)
        ON CONFLICT DO NOTHING
        """,
        nativeQuery = true
    )
    @Suppress("LongParameterList") // one parameter per inserted column
    fun insertIfAbsent(
        id: UUID,
        email: String,
        passwordHash: String,
        displayName: String,
        emailVerifiedAt: Instant?,
        createdAt: Instant,
    ): Int
}
