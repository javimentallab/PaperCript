package com.javimetallab.papercript

import com.javimetallab.papercript.crypto.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `honours the requested length`() {
        for (length in intArrayOf(8, 12, 24, 40, 64)) {
            assertEquals(length, PasswordGenerator.generate(length).length)
        }
    }

    @Test
    fun `out of range lengths are clamped`() {
        assertEquals(PasswordGenerator.MIN_LENGTH, PasswordGenerator.generate(1).length)
        assertEquals(PasswordGenerator.MAX_LENGTH, PasswordGenerator.generate(500).length)
    }

    @Test
    fun `always includes at least one character from each group`() {
        repeat(200) {
            val password = PasswordGenerator.generate(12, symbols = true)
            assertTrue("No lowercase: $password", password.any { it.isLowerCase() })
            assertTrue("No uppercase: $password", password.any { it.isUpperCase() })
            assertTrue("No digit: $password", password.any { it.isDigit() })
            assertTrue(
                "No symbol: $password",
                password.any { !it.isLetterOrDigit() }
            )
        }
    }

    @Test
    fun `no symbols appear when they are switched off`() {
        repeat(200) {
            val password = PasswordGenerator.generate(20, symbols = false)
            assertTrue(password.all { it.isLetterOrDigit() })
        }
    }

    @Test
    fun `uses the full set, including look-alike characters`() {
        // Nothing is barred for legibility: the password is never copied by
        // hand, it travels encrypted inside the QR.
        val sample = buildString {
            repeat(400) { append(PasswordGenerator.generate(64, symbols = true)) }
        }
        for (c in "lIO01") {
            assertTrue("'$c' should be able to appear", sample.contains(c))
        }
    }

    @Test
    fun `does not repeat itself`() {
        val a = PasswordGenerator.generate(32)
        val b = PasswordGenerator.generate(32)
        assertTrue(a != b)
    }

    @Test
    fun `estimated entropy grows with length and with symbols`() {
        val withoutSymbols = PasswordGenerator.entropyBits(20, symbols = false)
        val withSymbols = PasswordGenerator.entropyBits(20, symbols = true)
        assertTrue(withSymbols > withoutSymbols)
        assertTrue(PasswordGenerator.entropyBits(40, true) > withSymbols)
        // 24 characters over the full set should clear 150 bits.
        assertTrue(PasswordGenerator.entropyBits(24, true) > 150)
    }
}
