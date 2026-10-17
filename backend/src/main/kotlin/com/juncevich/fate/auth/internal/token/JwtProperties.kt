package com.juncevich.fate.auth.internal.token

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val accessSecret: String,
    val accessTtlMinutes: Long = 15,
    val refreshTtlDays: Long = 30,
    /**
     * How long a just-rotated refresh token is still accepted: covers concurrent refreshes, such
     * as two tabs restoring a session at once. A replay after this window revokes the session.
     */
    val refreshReuseGraceSeconds: Long = 30,
)
