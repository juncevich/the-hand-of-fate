package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.AccountDeleted
import com.juncevich.fate.auth.internal.port.UserRepositoryPort
import com.juncevich.fate.notification.NotificationOutbox
import com.juncevich.fate.shared.NotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@Service
@Transactional
class AccountDeletionService(
    private val users: UserRepositoryPort,
    private val outbox: NotificationOutbox,
    private val passwordConfirmation: PasswordConfirmation,
    private val loginThrottle: LoginThrottle,
    private val avatars: AvatarService,
    private val events: ApplicationEventPublisher,
) {
    /**
     * Deletes the account after checking its password: its own votes, sessions, tokens and avatar
     * go with it, other modules forget it via [AccountDeleted], queued emails to it are dropped.
     */
    fun deleteAccount(
        userId: UUID,
        password: String,
    ) {
        val user = users.findById(userId) ?: throw NotFoundException("User not found")
        passwordConfirmation.require(user, password)
        events.publishEvent(AccountDeleted(user.id, user.email))
        outbox.cancelFor(user.email)
        users.delete(user.id)
        loginThrottle.reset(user.email)
        // Object storage is not transactional: only remove the avatar once the deletion is committed.
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = avatars.deleteStoredObject(user.id)
            }
        )
    }
}
