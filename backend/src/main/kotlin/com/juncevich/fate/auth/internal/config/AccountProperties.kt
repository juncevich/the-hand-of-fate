package com.juncevich.fate.auth.internal.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Lifetimes of the one-time links mailed for email verification and password reset. */
@ConfigurationProperties(prefix = "app.auth")
data class AccountProperties(
    val verificationTokenTtl: Duration = Duration.ofHours(24),
    val passwordResetTokenTtl: Duration = Duration.ofMinutes(30),
    /** A new link for the same account and purpose is mailed at most this often. */
    val emailTokenCooldown: Duration = Duration.ofSeconds(60),
)
