package com.juncevich.fate.auth.internal.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Brute-force protection for the unauthenticated auth endpoints
 * (`/login`, `/register`, `/refresh`), keyed by client IP.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    /** Maximum requests permitted per client IP within [windowSeconds]. */
    val capacity: Int = 10,
    val windowSeconds: Long = 60,
)
