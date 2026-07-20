package com.javimetallab.papercript.crypto

/**
 * Crockford-style Base32: 32 symbols, without I, L, O or U.
 *
 * The code ends up printed on paper and may come back typed by hand, so the
 * alphabet rules out the expensive confusions: 0 against O, and 1 against I
 * against l.
 */
object Base32 {

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /**
     * Characters a human types believing they exist, mapped to the real symbol.
     * Only ones NOT in the alphabet belong here: 'S' and '5' look alike but both
     * are valid, so there the checksum decides.
     */
    private val ALIASES = mapOf(
        'O' to '0', 'o' to '0',
        'I' to '1', 'i' to '1', 'L' to '1', 'l' to '1', '|' to '1',
        'U' to 'V', 'u' to 'V'
    )

    private val DECODE: IntArray = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, c ->
            table[c.code] = index
            table[c.lowercaseChar().code] = index
        }
        ALIASES.forEach { (from, to) ->
            table[from.code] = ALPHABET.indexOf(to)
        }
    }

    /** Encodes bytes as Base32 text, unpadded. */
    fun encode(data: ByteArray): String {
        val out = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) {
            out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        }
        return out.toString()
    }

    /**
     * Decodes, skipping separators (hyphens, spaces, line breaks) and applying
     * the look-alike aliases.
     *
     * @throws IllegalArgumentException on a character that is neither in the
     *   alphabet nor a known alias.
     */
    fun decode(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(text.length * 5 / 8 + 1)
        var buffer = 0
        var bits = 0
        for (c in text) {
            if (c.isSeparator()) continue
            val value = if (c.code < 128) DECODE[c.code] else -1
            // The message is just the character: whoever displays it supplies the
            // wording in the right language.
            require(value >= 0) { c.toString() }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    /** Uppercases and resolves aliases, without decoding. */
    fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text) {
            if (c.isSeparator()) continue
            val value = if (c.code < 128) DECODE[c.code] else -1
            out.append(if (value >= 0) ALPHABET[value] else c.uppercaseChar())
        }
        return out.toString()
    }

    /**
     * Resolves aliases and uppercases WITHOUT changing the length or dropping
     * separators. Used while the user edits: any live regrouping would jump the
     * cursor to the end exactly when they are fixing a single character.
     */
    fun normalizeInPlace(text: String): String = buildString(text.length) {
        for (c in text) {
            if (c.isSeparator()) {
                append(c)
                continue
            }
            val value = if (c.code < 128) DECODE[c.code] else -1
            append(if (value >= 0) ALPHABET[value] else c.uppercaseChar())
        }
    }

    /** Splits into hyphen-separated groups of 4, for copying by hand. */
    fun group(text: String, size: Int = 4): String =
        text.chunked(size).joinToString("-")

    private fun Char.isSeparator(): Boolean =
        this == '-' || this == ' ' || this == '\n' || this == '\r' ||
            this == '\t' || this == '.' || this == ',' || this == '_'
}
