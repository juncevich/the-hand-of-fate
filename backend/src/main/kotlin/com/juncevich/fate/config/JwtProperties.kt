package com.juncevich.fate.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val accessSecret: String,
    val accessTtlMinutes: Long = 15,
    val refreshTtlDays: Long = 30,
)
