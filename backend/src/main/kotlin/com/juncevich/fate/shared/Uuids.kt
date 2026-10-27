package com.juncevich.fate.shared

import java.util.UUID

/**
 * RFC 9562 version-7 UUID: a 48-bit Unix-millisecond timestamp followed by random bits.
 * Values created later sort later, so new primary keys land at the end of B-tree indexes
 * instead of at random pages (as UUIDv4 does).
 *
 * For identifiers only — use [UUID.randomUUID] for secrets (tokens): v7 carries fewer
 * random bits and reveals its creation time.
 */
fun uuidV7(): UUID = UUID.ofEpochMillis(System.currentTimeMillis())
