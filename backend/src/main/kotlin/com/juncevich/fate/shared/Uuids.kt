package com.juncevich.fate.shared

import java.security.SecureRandom
import java.util.UUID

private val random = SecureRandom()

private const val VERSION_7 = 0x7000L
private const val VARIANT_RFC_4122 = Long.MIN_VALUE // 0b10 in the two top bits
private const val RAND_A_MASK = 0x0FFFL
private const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL

/**
 * RFC 9562 version-7 UUID: a 48-bit Unix-millisecond timestamp followed by random bits.
 * Values created later sort later, so new primary keys land at the end of B-tree indexes
 * instead of at random pages (as UUIDv4 does).
 *
 * For identifiers only — use [UUID.randomUUID] for secrets (tokens): v7 carries fewer
 * random bits and reveals its creation time.
 */
fun uuidV7(): UUID {
    val msb = (System.currentTimeMillis() shl 16) or VERSION_7 or (random.nextLong() and RAND_A_MASK)
    val lsb = VARIANT_RFC_4122 or (random.nextLong() and RAND_B_MASK)
    return UUID(msb, lsb)
}
