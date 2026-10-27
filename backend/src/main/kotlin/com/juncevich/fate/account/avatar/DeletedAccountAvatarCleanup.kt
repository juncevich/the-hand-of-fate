package com.juncevich.fate.account.avatar

import com.juncevich.fate.account.AccountDeleted
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Removes a deleted account's avatar object. Object storage is not transactional, so it happens
 * only once the deletion has committed — a rolled-back deletion keeps its avatar.
 */
@Component
class DeletedAccountAvatarCleanup(
    private val avatars: AvatarService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: AccountDeleted) = avatars.deleteStoredObject(event.userId)
}
