package dev.lantern.core.ulid

import java.security.SecureRandom

/**
 * Crockford-base32 ULID (26 chars). Matches contracts/schemas commandId pattern
 * `^[0-9A-HJKMNP-TV-Z]{26}$`.
 */
object Ulid {
    const val ALPHABET: String = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val random = SecureRandom()

    fun generate(nowMs: Long = System.currentTimeMillis(), entropy: ByteArray? = null): String {
        val ms = nowMs.coerceAtLeast(0)
        val rand = entropy ?: ByteArray(10).also { random.nextBytes(it) }
        require(rand.size >= 10) { "ULID entropy must be 10 bytes" }
        val chars = CharArray(26)
        encodeTime(ms, chars)
        encodeEntropy(rand, chars)
        return String(chars)
    }

    fun isValid(value: String): Boolean =
        value.length == 26 && value.all { it in ALPHABET }

    private fun encodeTime(ms: Long, chars: CharArray) {
        var remaining = ms
        for (i in 9 downTo 0) {
            chars[i] = ALPHABET[(remaining % 32).toInt()]
            remaining /= 32
        }
    }

    private fun encodeEntropy(entropy: ByteArray, chars: CharArray) {
        var acc = 0
        var bits = 0
        var index = 10
        for (b in entropy) {
            acc = (acc shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5 && index < 26) {
                bits -= 5
                chars[index++] = ALPHABET[(acc ushr bits) and 31]
            }
        }
        if (index < 26 && bits > 0) {
            chars[index] = ALPHABET[(acc shl (5 - bits)) and 31]
        }
    }
}
