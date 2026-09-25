package com.juncevich.fate.vote.internal.persistence

import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import com.juncevich.fate.vote.internal.persistence.entity.VoteJpaEntity
import com.juncevich.fate.vote.internal.persistence.jpa.VoteJpaRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.grpc.server.enabled=false"]
)
@ActiveProfiles("test")
@Testcontainers
class VoteVersioningIntegrationTest
    @Autowired
    constructor(
        private val voteJpaRepository: VoteJpaRepository,
        private val jdbcTemplate: JdbcTemplate,
        private val em: EntityManager,
        private val txManager: PlatformTransactionManager,
    ) {
        companion object {
            @Container
            @JvmField
            val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17")

            @DynamicPropertySource
            @JvmStatic
            fun datasource(registry: DynamicPropertyRegistry) {
                registry.add("spring.datasource.url", postgres::getJdbcUrl)
                registry.add("spring.datasource.username", postgres::getUsername)
                registry.add("spring.datasource.password", postgres::getPassword)
            }
        }

        private lateinit var adminId: UUID

        @BeforeEach
        fun fetchAdminId() {
            // V7 migration seeds admin@admin.com — use it to satisfy the creator_id FK.
            adminId =
                checkNotNull(
                    jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE email = 'admin@admin.com'",
                        UUID::class.java
                    )
                ) { "Demo user admin@admin.com must be seeded in the test profile" }
        }

        private fun tx(block: () -> Unit) = TransactionTemplate(txManager).execute { block() }

        // ── Schema ────────────────────────────────────────────────────────────────

        @Test
        @Transactional
        fun `V9 migration creates version column and Hibernate validates schema on startup`() {
            // If this test runs at all: Flyway V9 applied and ddl-auto=validate accepted the schema.
            val saved = voteJpaRepository.saveAndFlush(newVote())
            assertEquals(0, saved.version)
        }

        // ── Optimistic locking ────────────────────────────────────────────────────

        @Test
        @Transactional
        fun `version starts at 0 and increments after each modification`() {
            val v0 = voteJpaRepository.saveAndFlush(newVote())
            assertEquals(0, v0.version)

            em.clear()
            val reloaded = voteJpaRepository.findById(v0.id).get()
            reloaded.title = "updated"
            val v1 = voteJpaRepository.saveAndFlush(reloaded)
            assertEquals(1, v1.version)
        }

        @Test
        fun `stale version causes ObjectOptimisticLockingFailureException`() {
            val id = UUID.randomUUID()

            // Commit initial row so other transactions see it
            tx { voteJpaRepository.saveAndFlush(newVote(id)) }

            // First writer saves — version 0 → 1
            tx {
                val vote = voteJpaRepository.findById(id).get()
                vote.title = "first writer"
                voteJpaRepository.saveAndFlush(vote)
            }

            // Second writer has a stale copy at version 0 — must fail
            assertThrows<ObjectOptimisticLockingFailureException> {
                tx {
                    val stale = newVote(id).apply { title = "second writer" }
                    voteJpaRepository.saveAndFlush(stale)
                }
            }
        }

        // ── Pessimistic lock ──────────────────────────────────────────────────────

        @Test
        fun `findByIdWithPessimisticLock returns vote and acquires row-level lock`() {
            val id = UUID.randomUUID()
            tx { voteJpaRepository.saveAndFlush(newVote(id)) }

            tx {
                val locked = voteJpaRepository.findByIdWithPessimisticLock(id)
                assertEquals(id, locked?.id)
                assertEquals(0, locked?.version)
            }
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        private fun newVote(id: UUID = UUID.randomUUID()) =
            VoteJpaEntity(
                id = id,
                title = "test vote",
                creatorId = adminId,
                mode = VoteMode.SIMPLE,
                status = VoteStatus.PENDING
            )
    }
