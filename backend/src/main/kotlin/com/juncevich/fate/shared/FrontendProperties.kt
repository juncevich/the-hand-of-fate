package com.juncevich.fate.shared

import org.springframework.boot.context.properties.ConfigurationProperties

/** Where the SPA is served (`app.frontend-url`): the CORS origin and the base of links in emails. */
@ConfigurationProperties(prefix = "app")
data class FrontendProperties(
    val frontendUrl: String,
)
