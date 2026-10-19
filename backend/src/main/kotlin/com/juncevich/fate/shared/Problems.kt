package com.juncevich.fate.shared

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import java.time.Instant

/** The problem body every error response shares: English [title] plus the [code] clients translate. */
fun problem(
    status: HttpStatus,
    title: String,
    code: ErrorCode,
): ProblemDetail =
    ProblemDetail.forStatus(status).apply {
        this.title = title
        setProperty("code", code)
        setProperty("timestamp", Instant.now())
    }
