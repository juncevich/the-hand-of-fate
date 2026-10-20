package com.juncevich.fate.auth.internal.persistence.adapter

import com.juncevich.fate.auth.internal.persistence.entity.DeletedAccountJpaEntity
import com.juncevich.fate.auth.internal.persistence.jpa.DeletedAccountJpaRepository
import com.juncevich.fate.auth.internal.port.DeletedAccountRepositoryPort
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class DeletedAccountPersistenceAdapter(
    private val repository: DeletedAccountJpaRepository,
) : DeletedAccountRepositoryPort {
    override fun record(
        userId: UUID,
        deletedAt: Instant,
    ) {
        repository.save(DeletedAccountJpaEntity(userId, deletedAt))
    }

    override fun isDeleted(userId: UUID): Boolean = repository.existsById(userId)

    override fun findPendingIdentityDeletion(limit: Int): List<UUID> =
        repository.findPendingIdentityDeletion(Limit.of(limit))

    override fun markIdentityDeleted(
        userId: UUID,
        at: Instant,
    ) {
        repository.markIdentityDeleted(userId, at)
    }

    override fun purge(before: Instant): Int = repository.purge(before)
}
