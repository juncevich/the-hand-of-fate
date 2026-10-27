package com.juncevich.fate.notification

import com.juncevich.fate.notification.port.EmailSuppressionPort
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Whether an address receives notification emails (invitations, draw results). Every such
 * email links to an unsubscribe page signed for its recipient, so the link works without an
 * account or a session, and can't be forged for someone else's address. Account emails
 * (verification, password reset) are always delivered.
 */
@Service
@Transactional
class EmailPreferences(
    private val suppressions: EmailSuppressionPort,
    @Value("\${app.notifications.unsubscribe-secret}") secret: String,
) {
    private val key = SecretKeySpec(secret.toByteArray(), HMAC)

    @Transactional(readOnly = true)
    fun receivesNotifications(email: Email): Boolean = !suppressions.isSuppressed(email)

    /** Opt-out from a mailed link: [email] as the link carries it, [signature] must be [sign] of the address. */
    fun unsubscribe(
        email: String,
        signature: String,
    ) {
        val normalized = Email.of(email)
        val expected = sign(normalized).toByteArray()
        if (!MessageDigest.isEqual(expected, signature.lowercase().toByteArray())) {
            throw BadRequestException("This unsubscribe link is invalid", ErrorCode.INVALID_TOKEN)
        }
        suppressions.suppress(normalized)
    }

    /** Settings toggle of a signed-in account for its own address. */
    fun setReceivesNotifications(
        email: Email,
        enabled: Boolean,
    ) {
        if (enabled) suppressions.allow(email) else suppressions.suppress(email)
    }

    /** HMAC-SHA256 of the canonical address, hex; the `sig` of its unsubscribe link. */
    fun sign(email: Email): String {
        val mac = Mac.getInstance(HMAC).apply { init(key) }
        return HexFormat.of().formatHex(mac.doFinal(email.value.toByteArray()))
    }

    private companion object {
        const val HMAC = "HmacSHA256"
    }
}
