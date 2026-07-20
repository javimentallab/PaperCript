package com.javimetallab.papercript.crypto

import java.security.SecureRandom

/**
 * Password generator over the full printable ASCII set, 94 characters, with
 * nothing excluded: the password is never transcribed by hand, it travels
 * encrypted inside the QR and goes from the screen to the clipboard.
 *
 * Each character contributes log2(94) = 6.55 bits.
 */
object PasswordGenerator {

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    const val DEFAULT_LENGTH = 24

    private val random = SecureRandom()

    /**
     * @param length character count, between [MIN_LENGTH] and [MAX_LENGTH].
     * @param symbols include punctuation. Kept optional because some services
     *   still reject it, not for legibility.
     */
    fun generate(
        length: Int = DEFAULT_LENGTH,
        symbols: Boolean = true
    ): String {
        val size = length.coerceIn(MIN_LENGTH, MAX_LENGTH)

        val pools = buildList {
            add(LOWER)
            add(UPPER)
            add(DIGITS)
            if (symbols) add(SYMBOLS)
        }
        val all = pools.joinToString("")

        // One character from each group first, so it always satisfies the usual
        // form rules, then the rest uniformly over the whole set.
        val chars = MutableList(size) { index ->
            val pool = if (index < pools.size) pools[index] else all
            pool[random.nextInt(pool.length)]
        }

        // Fisher-Yates shuffle so those fixed positions are not predictable.
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return chars.joinToString("")
    }

    /** Rough entropy in bits, to show the user. */
    fun entropyBits(length: Int, symbols: Boolean): Int {
        val alphabet = LOWER.length + UPPER.length + DIGITS.length +
            if (symbols) SYMBOLS.length else 0
        return (length.coerceIn(MIN_LENGTH, MAX_LENGTH) *
            (Math.log(alphabet.toDouble()) / Math.log(2.0))).toInt()
    }
}
