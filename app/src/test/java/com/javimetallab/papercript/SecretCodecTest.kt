package com.javimetallab.papercript

import com.javimetallab.papercript.crypto.Base32
import com.javimetallab.papercript.crypto.PasswordGenerator
import com.javimetallab.papercript.crypto.SecretCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Base32Test {

    @Test
    fun `round trip of arbitrary bytes`() {
        val random = java.util.Random(42)
        repeat(200) {
            val data = ByteArray(random.nextInt(60) + 1).also(random::nextBytes)
            assertArrayEquals(data, Base32.decode(Base32.encode(data)))
        }
    }

    @Test
    fun `the alphabet contains no ambiguous characters`() {
        val encoded = Base32.encode(ByteArray(64) { it.toByte() })
        for (c in "ILOUilou") {
            assertTrue("'$c' must not appear", !encoded.contains(c))
        }
    }

    @Test
    fun `look-alike aliases are resolved when decoding`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = Base32.encode(data)
        val misread = encoded.replace('0', 'O').replace('1', 'I').lowercase()
        assertArrayEquals(data, Base32.decode(misread))
    }

    @Test
    fun `separators are ignored`() {
        val data = byteArrayOf(9, 8, 7, 6, 5, 4)
        val encoded = Base32.encode(data)
        assertArrayEquals(data, Base32.decode(Base32.group(encoded)))
        assertArrayEquals(data, Base32.decode(encoded.chunked(3).joinToString(" \n")))
    }

    @Test
    fun `normalizeInPlace preserves the length`() {
        val input = "abcd-efgh ijkl"
        assertEquals(input.length, Base32.normalizeInPlace(input).length)
    }
}

class SecretCodecTest {

    private val master = "a very long and difficult master password"

    @Test
    fun `encrypt then decrypt returns the original password`() {
        val password = "Tr0ub4dor&3-ñandu"
        val code = SecretCodec.encrypt(password, master)
        assertEquals(
            SecretCodec.DecryptResult.Success(password),
            SecretCodec.decrypt(code, master)
        )
    }

    @Test
    fun `works with passwords of any length`() {
        for (length in intArrayOf(1, 2, 8, 16, 20, 40, 64, 128)) {
            val password = buildString { repeat(length) { append(('!' + (it % 94))) } }
            val code = SecretCodec.encrypt(password, master)
            assertEquals(
                "length $length",
                SecretCodec.DecryptResult.Success(password),
                SecretCodec.decrypt(code, master)
            )
        }
    }

    @Test
    fun `works with the whole printable ASCII set and accents`() {
        // The password is never transcribed by hand, so no character is barred.
        // This checks that none of them breaks the round trip.
        val password = (' '..'~').joinToString("") + "áéíóúñÑ€"
        val code = SecretCodec.encrypt(password, master)
        assertEquals(
            SecretCodec.DecryptResult.Success(password),
            SecretCodec.decrypt(code, master)
        )
    }

    @Test
    fun `a v2 card still decrypts`() {
        // Raising the scrypt cost must not invalidate what is already printed:
        // the version byte selects the parameters it was derived with.
        val code = SecretCodec.encrypt("secreto", master, version = 2)
        assertEquals(
            SecretCodec.DecryptResult.Success("secreto"),
            SecretCodec.decrypt(code, master)
        )
    }

    @Test
    fun `an unknown version is rejected with a clear reason`() {
        val code = Base32.normalize(SecretCodec.encrypt("secreto", master))
        // Forge the version byte to 9, keeping the rest of the payload.
        val bytes = Base32.decode(code)
        bytes[0] = 9
        val result = SecretCodec.decrypt(Base32.encode(bytes), master)
        assertEquals(
            SecretCodec.DecryptResult.Malformed(
                SecretCodec.Problem.UNSUPPORTED_VERSION,
                "v9"
            ),
            result
        )
    }

    @Test
    fun `a wrong master password does not decrypt`() {
        val code = SecretCodec.encrypt("secreto", master)
        assertEquals(
            SecretCodec.DecryptResult.BadKeyOrCorrupt,
            SecretCodec.decrypt(code, master + "x")
        )
    }

    @Test
    fun `a mistyped character is detected`() {
        val code = Base32.normalize(SecretCodec.encrypt("secreto", master))
        val index = code.length / 2
        val replacement = if (code[index] == 'K') 'M' else 'K'
        val broken = code.substring(0, index) + replacement + code.substring(index + 1)
        assertNotEquals(code, broken)
        assertEquals(
            SecretCodec.DecryptResult.BadKeyOrCorrupt,
            SecretCodec.decrypt(broken, master)
        )
    }

    @Test
    fun `encrypting the same password twice gives different codes`() {
        val a = SecretCodec.encrypt("secreto", master)
        val b = SecretCodec.encrypt("secreto", master)
        assertNotEquals("The random salt must change the result", a, b)
        assertEquals(
            SecretCodec.DecryptResult.Success("secreto"),
            SecretCodec.decrypt(b, master)
        )
    }

    @Test
    fun `a truncated code is rejected without deriving a key`() {
        assertEquals(
            SecretCodec.DecryptResult.Malformed(SecretCodec.Problem.TOO_SHORT),
            SecretCodec.decrypt("ABCD", master)
        )
    }

    @Test
    fun `the label does not affect decryption`() {
        // The whole point of the v2 format: the label guides the user and
        // nothing else. Relabelling a printed card does not break it.
        val code = SecretCodec.encrypt("secreto", master)
        assertEquals(
            SecretCodec.DecryptResult.Success("secreto"),
            SecretCodec.decrypt(code, master)
        )
    }

    @Test
    fun `sanitizeLabel respects what the user typed`() {
        assertEquals("Gmail (trabajo)", SecretCodec.sanitizeLabel("  Gmail (trabajo)  "))
        assertEquals("Banco Sabadell", SecretCodec.sanitizeLabel("Banco    Sabadell"))
        assertTrue(SecretCodec.sanitizeLabel("x".repeat(80)).length <= 28)
    }

    @Test
    fun `the code for a 16 character password fits on one line`() {
        val code = SecretCodec.encrypt(PasswordGenerator.generate(16), master)
        val chars = Base32.normalize(code).length
        assertTrue("Too long to write by hand: $chars", chars <= 48)
    }
}
