package com.juncevich.fate.account.deletion

import com.juncevich.fate.account.AccountDeleted
import com.juncevich.fate.account.AuthenticatedUser
import com.juncevich.fate.account.avatar.AvatarService
import com.juncevich.fate.account.domain.DeletedAccountRepository
import com.juncevich.fate.account.domain.UserRepository
import com.juncevich.fate.account.keycloak.KeycloakProperties
import com.juncevich.fate.notification.NotificationOutbox
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

@Service
@Transactional
class AccountDeletionService(
    private val users: UserRepository,
    private val outbox: NotificationOutbox,
    private val avatars: AvatarService,
    private val events: ApplicationEventPublisher,
    private val deletedAccounts: DeletedAccountRepository,
    private val keycloak: KeycloakProperties,
) {
    /**
     * Deletes the account once the caller proved it is them by a recent sign-in: its own votes,
     * tokens and avatar go with it, other modules forget it via [AccountDeleted], queued emails to it
     * are dropped. Its Keycloak user is deleted after the commit by [IdentityDeletionWorker].
     */
    fun deleteAccount(caller: AuthenticatedUser) {
        requireRecentSignIn(caller)
        val user = users.findById(caller.id) ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)
        events.publishEvent(AccountDeleted(user.id, user.email))
        outbox.cancelFor(user.email)
        users.delete(user.id)
        deletedAccounts.record(user.id, Instant.now())
        // Object storage is not transactional: only remove the avatar once the deletion is committed.
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = avatars.deleteStoredObject(user.id)
            }
        )
    }

    private fun requireRecentSignIn(caller: AuthenticatedUser) {
        val signedInAt = caller.authenticatedAt
        if (signedInAt == null || signedInAt.isBefore(Instant.now().minus(keycloak.reauthenticationMaxAge))) {
            throw ForbiddenException("Sign in again to confirm it is you", ErrorCode.REAUTHENTICATION_REQUIRED)
        }
    }
}
