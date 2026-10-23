package com.juncevich.fate.account.persistence.jpa

import com.juncevich.fate.account.persistence.entity.UserJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

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
