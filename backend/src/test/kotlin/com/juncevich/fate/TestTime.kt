package com.juncevich.fate

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** "Now" in unit tests: services get [FIXED_CLOCK], domain calls get [NOW]. */
val NOW: Instant = Instant.parse("2026-01-01T12:00:00Z")

val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
