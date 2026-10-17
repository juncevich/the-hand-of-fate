package com.juncevich.fate.vote.internal

import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat

private const val SEED_BYTES = 32
private const val COUNTER_BYTES = Int.SIZE_BYTES

/** What a recorded draw needs to be recomputed: the seed and the candidates in canonical order. */
data class DrawAudit(
    /** 32 random bytes, hex-encoded. */
    val seed: String,
    /** Participant emails or option ids, sorted; the winner is `candidates[WinnerPicker.index(seed, size)]`. */
    val candidates: List<String>,
)

/**
 * Picks draw winners reproducibly: a fresh seed from [SecureRandom] per draw, and a winner index
 * derived from it with SHA-256, so anyone holding a [DrawAudit] can recompute the result.
 * This proves the recorded winner follows from the recorded seed — not that the seed itself was
 * not chosen by the server, which would need a commit-reveal scheme.
 */
@Component
class WinnerPicker(
    private val random: SecureRandom = SecureRandom(),
) {
    /** Picks one of [candidateKeys] (any order; they are sorted first) and returns it with its audit record. */
    fun pick(candidateKeys: Collection<String>): Pair<String, DrawAudit> {
        require(candidateKeys.isNotEmpty()) { "Nothing to draw from" }
        val candidates = candidateKeys.distinct().sorted()
        val seed = HexFormat.of().formatHex(ByteArray(SEED_BYTES).also(random::nextBytes))
        return candidates[index(seed, candidates.size)] to DrawAudit(seed, candidates)
    }

    companion object {
        /**
         * Uniform index in `0 until size` from [seed]: the first 63 bits of
         * SHA-256(seed bytes ‖ counter as big-endian int), for counter = 0, 1, …, skipping values
         * from the incomplete last block of `size` so that no index is favoured (no modulo bias).
         */
        fun index(
            seed: String,
            size: Int,
        ): Int {
            require(size > 0) { "size must be positive" }
            val seedBytes = HexFormat.of().parseHex(seed)
            // 2^63 values; accepting only the first 2^63 - (2^63 mod size) of them leaves whole blocks.
            val excess = (Long.MAX_VALUE % size + 1) % size
            var counter = 0
            while (true) {
                val digest =
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(seedBytes + ByteBuffer.allocate(COUNTER_BYTES).putInt(counter).array())
                val value = ByteBuffer.wrap(digest).long ushr 1
                if (value <= Long.MAX_VALUE - excess) return (value % size).toInt()
                counter++
            }
        }
    }
}
