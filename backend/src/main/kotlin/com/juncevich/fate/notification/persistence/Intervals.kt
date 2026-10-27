package com.juncevich.fate.notification.persistence

import java.time.Duration

private const val MILLIS_PER_SECOND = 1000.0

/** The duration in (fractional) seconds, as PostgreSQL's `make_interval(secs => …)` takes it. */
internal fun Duration.seconds(): Double = toMillis() / MILLIS_PER_SECOND
