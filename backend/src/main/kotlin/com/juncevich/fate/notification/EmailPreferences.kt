package com.juncevich.fate.notification

import com.juncevich.fate.notification.internal.port.EmailSuppressionPort
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.normalizeEmail
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
    @param:Value("\${app.notifications.unsubscribe-secret}") secret: String,
) {
    private val key = SecretKeySpec(secret.toByteArray(), HMAC)

    @Transactional(readOnly = true)
    fun receivesNotifications(email: String): Boolean = !suppressions.isSuppressed(normalizeEmail(email))

    /** Opt-out from a mailed link: [signature] must be [sign] of the address. */
    fun unsubscribe(
        email: String,
        signature: String,
    ) {
        val normalized = normalizeEmail(email)
        val expected = sign(normalized).toByteArray()
        if (!MessageDigest.isEqual(expected, signature.lowercase().toByteArray())) {
            throw BadRequestException("This unsubscribe link is invalid", ErrorCode.INVALID_TOKEN)
        }
        suppressions.suppress(normalized)
    }

    /** Settings toggle of a signed-in account for its own address. */
    fun setReceivesNotifications(
        email: String,
        enabled: Boolean,
    ) {
        val normalized = normalizeEmail(email)
        if (enabled) suppressions.allow(normalized) else suppressions.suppress(normalized)
    }

    /** HMAC-SHA256 of the normalized address, hex; the `sig` of its unsubscribe link. */
    fun sign(email: String): String {
        val mac = Mac.getInstance(HMAC).apply { init(key) }
        return HexFormat.of().formatHex(mac.doFinal(email.toByteArray()))
    }

    private companion object {
        const val HMAC = "HmacSHA256"
    }
}
