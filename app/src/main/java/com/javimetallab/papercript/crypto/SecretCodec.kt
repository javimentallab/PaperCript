package com.javimetallab.papercript.crypto

import org.bouncycastle.crypto.generators.SCrypt
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts a password into a printable code, and decrypts it back.
 *
 * Format (Crockford Base32, grouped in fours):
 *
 *     [ version 1B ][ salt 4B ][ ciphertext NB ][ tag 5B ]
 *
 * - Key derived with scrypt from (master password, domain, version, salt). The
 *   label is NOT part of it: it is the user's own note, so changing it or
 *   misspelling it can never render a card undecryptable.
 * - AES-256-CTR with a fixed IV. Safe here because the salt is random per
 *   encryption and the key therefore never repeats, so no keystream is reused.
 * - Integrity via HMAC-SHA256 truncated to 40 bits (encrypt-then-MAC). It
 *   catches a mistyped character and a wrong master password alike, which is
 *   why the two are indistinguishable on failure.
 *
 * The code length reveals the password length. It is not padded, so as not to
 * lengthen the rescue text printed on the card.
 */
object SecretCodec {

    /**
     * Version used for new encryptions. Older ones stay decryptable: the version
     * byte selects the parameters, so a card printed months ago keeps working
     * after the cost is raised.
     *
     * - v1 (withdrawn): the label was part of the key derivation.
     * - v2: the label became a plain user note. scrypt at 32 MB.
     * - v3: same format as v2, scrypt at 64 MB.
     */
    private const val VERSION: Byte = 3
    private const val SALT_LEN = 4
    private const val TAG_LEN = 5
    private const val KEY_LEN = 32

    /**
     * scrypt parameters per version.
     *
     * Memory cost is 128 * r * N bytes, and that is what really prices an
     * attack: GPUs can multiply the number of guesses in parallel, but cannot
     * hand 64 MB of fast RAM to each one.
     */
    private data class Params(val n: Int, val r: Int, val p: Int, val domain: String)

    /**
     * The domain string is an ingredient of the derivation, not a label: it
     * keeps these keys apart from those of any other program deriving with
     * scrypt from the same master password.
     *
     * FROZEN. The key is stored nowhere and is recomputed in full on every
     * decryption; touching these values makes already-printed cards unreadable
     * forever. A new format gets its own entry here instead.
     */
    private val PARAMS: Map<Byte, Params> = mapOf(
        2.toByte() to Params(n = 32768, r = 8, p = 1, domain = "papercript/v2"),
        3.toByte() to Params(n = 65536, r = 8, p = 1, domain = "papercript/v3")
    )

    private val random = SecureRandom()

    /** Shortest a valid payload can be (a one-character password). */
    const val MIN_PAYLOAD_BYTES = 1 + SALT_LEN + 1 + TAG_LEN

    /**
     * Reasons a code never even reaches a decryption attempt.
     *
     * The reason is returned rather than a message: user-facing text lives in
     * resources and gets translated, and this layer knows nothing about
     * languages.
     */
    enum class Problem {
        EMPTY_MASTER,
        INVALID_CHARACTER,
        TOO_SHORT,
        UNSUPPORTED_VERSION
    }

    sealed interface DecryptResult {
        data class Success(val password: String) : DecryptResult

        /** Wrong master password or a misread character. Indistinguishable. */
        data object BadKeyOrCorrupt : DecryptResult

        data class Malformed(val problem: Problem, val detail: String? = null) : DecryptResult
    }

    /**
     * Encrypts [password] and returns the card code, already grouped.
     *
     * [version] exists for the tests, so they can produce a card in an older
     * format and check that it still decrypts.
     */
    fun encrypt(password: String, master: String, version: Byte = VERSION): String {
        require(password.isNotEmpty()) { "The password is empty" }
        require(master.isNotEmpty()) { "The master password is empty" }
        require(PARAMS.containsKey(version)) { "Unknown version: $version" }

        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val header = byteArrayOf(version) + salt
        val (encKey, macKey) = deriveKeys(master, salt, version)

        try {
            val plaintext = password.toByteArray(Charsets.UTF_8)
            val ciphertext = aesCtr(Cipher.ENCRYPT_MODE, encKey, plaintext)
            val tag = tag(macKey, header + ciphertext)
            return Base32.group(Base32.encode(header + ciphertext + tag))
        } finally {
            encKey.fill(0)
            macKey.fill(0)
        }
    }

    /**
     * Decrypts a card code. Accepts hyphens, spaces and line breaks, and
     * resolves the look-alike aliases (O->0, I/L->1, U->V).
     */
    fun decrypt(text: String, master: String): DecryptResult {
        if (master.isEmpty()) return DecryptResult.Malformed(Problem.EMPTY_MASTER)

        val payload = try {
            Base32.decode(text)
        } catch (e: IllegalArgumentException) {
            return DecryptResult.Malformed(Problem.INVALID_CHARACTER, e.message)
        }

        if (payload.size < MIN_PAYLOAD_BYTES) {
            return DecryptResult.Malformed(Problem.TOO_SHORT)
        }
        val version = payload[0]
        if (!PARAMS.containsKey(version)) {
            return DecryptResult.Malformed(Problem.UNSUPPORTED_VERSION, "v$version")
        }

        val salt = payload.copyOfRange(1, 1 + SALT_LEN)
        val ciphertext = payload.copyOfRange(1 + SALT_LEN, payload.size - TAG_LEN)
        val received = payload.copyOfRange(payload.size - TAG_LEN, payload.size)
        val header = payload.copyOfRange(0, 1 + SALT_LEN)

        val (encKey, macKey) = deriveKeys(master, salt, version)
        try {
            val expected = tag(macKey, header + ciphertext)
            if (!MessageDigest.isEqual(expected, received)) {
                return DecryptResult.BadKeyOrCorrupt
            }
            val plaintext = aesCtr(Cipher.DECRYPT_MODE, encKey, ciphertext)
            return DecryptResult.Success(String(plaintext, Charsets.UTF_8))
        } finally {
            encKey.fill(0)
            macKey.fill(0)
        }
    }

    /**
     * Whether a code is of plausible length, without deriving any key. Avoids
     * running scrypt over an obviously incomplete entry.
     */
    fun looksComplete(text: String): Boolean {
        val clean = Base32.normalize(text)
        return clean.length * 5 / 8 >= MIN_PAYLOAD_BYTES
    }

    /**
     * Tidies a label for display and printing.
     *
     * Purely cosmetic: the label never touches the cryptography, so case,
     * accents and spaces are preserved and the user can write "Gmail (work)" if
     * that helps. Only the length is capped, to fit on the printed card.
     */
    fun sanitizeLabel(label: String): String =
        label.trim().replace(Regex("\\s+"), " ").take(28)

    private fun deriveKeys(
        master: String,
        salt: ByteArray,
        version: Byte
    ): Pair<ByteArray, ByteArray> {
        val params = requireNotNull(PARAMS[version]) { "Unknown version: $version" }

        val scryptSalt = params.domain.toByteArray(Charsets.UTF_8) +
            byteArrayOf(version) +
            salt

        val derived = SCrypt.generate(
            master.toByteArray(Charsets.UTF_8),
            scryptSalt,
            params.n,
            params.r,
            params.p,
            KEY_LEN * 2
        )
        val encKey = derived.copyOfRange(0, KEY_LEN)
        val macKey = derived.copyOfRange(KEY_LEN, KEY_LEN * 2)
        derived.fill(0)
        return encKey to macKey
    }

    private fun aesCtr(mode: Int, key: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        return cipher.doFinal(input)
    }

    private fun tag(macKey: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        return mac.doFinal(data).copyOfRange(0, TAG_LEN)
    }
}
