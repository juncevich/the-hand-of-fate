package com.juncevich.fate.notification

import com.juncevich.fate.notification.internal.port.NotificationOutboxPort
import com.juncevich.fate.notification.internal.port.NotificationType
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Entry point for other modules: records one email for later delivery by the outbox worker.
 * Call it inside the transaction that causes the email, so both commit or roll back together.
 */
@Component
class NotificationOutbox(
    private val outbox: NotificationOutboxPort,
    private val json: JsonMapper,
) {
    fun record(payload: NotificationPayload) {
        outbox.append(payload.type, json.writeValueAsString(payload))
    }

    private val NotificationPayload.type: NotificationType
        get() =
            when (this) {
                is InvitationPayload -> NotificationType.INVITATION
                is DrawResultPayload -> NotificationType.DRAW_RESULT
                is EmailVerificationPayload -> NotificationType.EMAIL_VERIFICATION
                is PasswordResetPayload -> NotificationType.PASSWORD_RESET
            }
}
