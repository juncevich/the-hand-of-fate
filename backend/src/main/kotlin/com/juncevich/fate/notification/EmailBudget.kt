package com.juncevich.fate.notification

import com.juncevich.fate.notification.internal.NotificationProperties
import com.juncevich.fate.notification.internal.port.EmailBudgetPort
import com.juncevich.fate.shared.TooManyRequestsException
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Caps the emails one user can make the service send within a rolling window
 * (`app.notifications.email-budget-per-user` per `email-budget-window`), so an account cannot
 * turn invitations or repeated draws into a spam relay. Must be called inside the transaction
 * that records the notifications: the reservation commits or rolls back with them.
 */
@Component
class EmailBudget(
    private val port: EmailBudgetPort,
    private val properties: NotificationProperties,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Reserves [recipients] emails for [userId] or rejects the whole action. */
    fun reserveOrThrow(
        userId: UUID,
        recipients: Int,
    ) {
        if (!tryReserve(userId, recipients)) {
            throw TooManyRequestsException(
                "Email limit reached: at most ${properties.emailBudgetPerUser} notification emails " +
                    "per ${properties.emailBudgetWindow.toHours()} h. Try again later or invite fewer participants."
            )
        }
    }

    /**
     * Reserves [recipients] emails for [userId] if they fit the budget; `false` means the
     * caller must not send them. Never partially reserves.
     */
    fun tryReserve(
        userId: UUID,
        recipients: Int,
    ): Boolean {
        if (recipients <= 0) return true
        port.lock(userId)
        val used = port.usedWithin(userId, properties.emailBudgetWindow)
        if (used + recipients > properties.emailBudgetPerUser) {
            log.warn("Email budget exceeded for user {}: {} used, {} requested", userId, used, recipients)
            meterRegistry.counter("notification.suppressed").increment(recipients.toDouble())
            return false
        }
        port.record(userId, recipients)
        return true
    }

    @Scheduled(fixedDelayString = "\${app.notifications.purge-delay-ms:3600000}")
    fun purgeExpired() {
        val purged = port.purgeOlderThan(properties.emailBudgetWindow)
        if (purged > 0) log.debug("Purged {} expired email budget entries", purged)
    }
}
