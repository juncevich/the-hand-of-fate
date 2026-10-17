package com.juncevich.fate.auth.internal.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Brute-force protection for the unauthenticated auth endpoints: a request budget per client IP
 * on `/login`, `/register`, `/refresh`, plus a failed-login budget per account (`LoginThrottle`).
 */
@ConfigurationProperties(prefix = "app.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    /** Maximum requests permitted per client IP within [windowSeconds]. */
    val capacity: Int = 10,
    val windowSeconds: Long = 60,
    /** Failed logins permitted per account within [loginFailureWindowSeconds], from any IP. */
    val loginFailuresPerAccount: Int = 10,
    val loginFailureWindowSeconds: Long = 900,
)
