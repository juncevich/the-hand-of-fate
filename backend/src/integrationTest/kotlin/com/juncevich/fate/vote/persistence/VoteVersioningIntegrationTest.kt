package com.juncevich.fate.vote.persistence

import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.vote.DrawTarget
import com.juncevich.fate.vote.VoteMode
import com.juncevich.fate.vote.VoteStatus
import com.juncevich.fate.vote.domain.Vote
import com.juncevich.fate.vote.domain.VoteRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
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
import java.time.Instant
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
        private val voteRepository: VoteRepository,
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

        private lateinit var creatorId: UUID

        @BeforeEach
        fun createCreator() {
            // Any account satisfies the creator_id FK.
            creatorId = UUID.randomUUID()
            jdbcTemplate.update(
                "INSERT INTO users (id, email, display_name) VALUES (?, ?, 'Creator')",
                creatorId,
                "creator-$creatorId@test.com"
            )
        }

        private fun tx(block: () -> Unit) = TransactionTemplate(txManager).execute { block() }

        // ── Schema ────────────────────────────────────────────────────────────────

        @Test
        @Transactional
        fun `V9 migration creates version column and Hibernate validates schema on startup`() {
            // If this test runs at all: Flyway V9 applied and ddl-auto=validate accepted the schema.
            val saved = persist(newVote())
            assertEquals(0, saved.version)
        }

        // ── Optimistic locking ────────────────────────────────────────────────────

        @Test
        @Transactional
        fun `version starts at 0 and increments after each modification`() {
            val v0 = persist(newVote())
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
            tx { persist(newVote(id)) }

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
            tx { persist(newVote(id)) }

            tx {
                val locked = voteJpaRepository.findByIdWithPessimisticLock(id)
                assertEquals(id, locked?.id)
                assertEquals(0, locked?.version)
            }
        }

        // ── Aggregate adapter ─────────────────────────────────────────────────────

        @Test
        fun `a saved vote round-trips with its participants and options`() {
            val vote = newAggregate(listOf("p@test.com"), listOf("A", "B"))
            tx { voteRepository.save(vote) }

            val loaded = checkNotNull(voteRepository.find(vote.id))
            assertEquals(listOf("creator-$creatorId@test.com", "p@test.com"), loaded.participants.map { it.email.value })
            assertEquals(listOf("A", "B"), loaded.options.map { it.title })
            assertEquals(vote.participants.map { it.id }, loaded.participants.map { it.id })
            assertEquals(DrawTarget.OPTIONS, loaded.drawTarget)
            assertEquals(0, loaded.version)
        }

        @Test
        fun `saving a changed vote adds, updates and removes child rows`() {
            val vote = newAggregate(listOf("p@test.com", "q@test.com"), listOf("A", "B", "C"))
            tx { voteRepository.save(vote) }

            tx {
                val locked = checkNotNull(voteRepository.findForUpdate(vote.id))
                val (a, b, c) = locked.options
                locked.removeParticipant(UserId(creatorId), "q@test.com")
                locked.invite(UserId(creatorId), "r@test.com", Instant.now()) { "Ray" }
                locked.removeOption(UserId(creatorId), b.id)
                // Swaps positions within the transaction: (vote_id, position) is only checked at commit.
                locked.reorderOptions(UserId(creatorId), listOf(c.id, a.id))
                locked.addOption(UserId(creatorId), "D", Instant.now())
                voteRepository.save(locked)
            }

            val loaded = checkNotNull(voteRepository.find(vote.id))
            assertEquals(
                setOf("creator-$creatorId@test.com", "p@test.com", "r@test.com"),
                loaded.participants.map { it.email.value }.toSet()
            )
            assertEquals("Ray", loaded.participants.single { it.email.value == "r@test.com" }.displayName)
            assertEquals(listOf("C" to 0, "A" to 1, "D" to 2), loaded.options.map { it.title to it.position })
            // Child rows are owned by the inverse side: changing them leaves the vote row and its version alone.
            assertEquals(0, loaded.version)
            assertEquals(3, jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM vote_options WHERE vote_id = ?", vote.id.value))
        }

        @Test
        fun `a save from a stale copy of the vote is rejected`() {
            val vote = newAggregate(emptyList(), listOf("A"))
            tx { voteRepository.save(vote) }
            val stale = checkNotNull(voteRepository.find(vote.id))

            // Another writer changes the vote row in between, which bumps its version.
            tx {
                val current = checkNotNull(voteRepository.findForUpdate(vote.id))
                current.close(UserId(creatorId))
                voteRepository.save(current)
            }

            stale.addOption(UserId(creatorId), "B", Instant.now())
            assertThrows<ObjectOptimisticLockingFailureException> { tx { voteRepository.save(stale) } }
            assertEquals(listOf("A"), checkNotNull(voteRepository.find(vote.id)).options.map { it.title })
        }

        @Test
        fun `deleting a vote removes its children in one statement`() {
            val vote = newAggregate(listOf("p@test.com"), listOf("A"))
            tx { voteRepository.save(vote) }

            tx { voteRepository.delete(vote.id) }

            assertEquals(null, voteRepository.find(vote.id))
            assertEquals(0, jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM vote_participants WHERE vote_id = ?", vote.id.value))
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        private fun newAggregate(
            invited: List<String>,
            options: List<String>,
        ) = Vote.create(
            creatorId = UserId(creatorId),
            creatorEmail = Email.of("creator-$creatorId@test.com"),
            title = "aggregate",
            description = null,
            mode = VoteMode.SIMPLE,
            drawTarget = null,
            participantEmails = invited,
            optionTitles = options,
            now = Instant.now()
        ) { emptyMap() }

        // persist, as VoteRepositoryAdapter inserts: merging a new entity (saveAndFlush) inserts and then updates it.
        private fun persist(vote: VoteJpaEntity): VoteJpaEntity =
            vote.also {
                em.persist(it)
                em.flush()
            }

        private fun newVote(id: UUID = UUID.randomUUID()) =
            VoteJpaEntity(
                id = id,
                title = "test vote",
                creatorId = creatorId,
                mode = VoteMode.SIMPLE,
                status = VoteStatus.PENDING,
                createdAt = Instant.now()
            )
    }
