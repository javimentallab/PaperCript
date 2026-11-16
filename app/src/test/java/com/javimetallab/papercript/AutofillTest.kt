package com.javimetallab.papercript

import android.text.InputType
import com.javimetallab.papercript.autofill.DecryptedForFill
import com.javimetallab.papercript.autofill.PasswordFields
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutofillTest {

    private var now = 0L

    @Before
    fun fakeClock() {
        now = 1_000_000L
        DecryptedForFill.clock = { now }
        DecryptedForFill.clear()
    }

    @After
    fun forget() = DecryptedForFill.clear()

    private fun text(variation: Int) = InputType.TYPE_CLASS_TEXT or variation

    @Test
    fun `password input types are detected`() {
        for (variation in intArrayOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) {
            assertTrue(PasswordFields.isPassword(null, text(variation), null, null))
        }
        assertTrue(
            PasswordFields.isPassword(
                null, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, null, null
            )
        )
    }

    @Test
    fun `ordinary fields are not passwords`() {
        assertFalse(PasswordFields.isPassword(null, text(InputType.TYPE_TEXT_VARIATION_NORMAL), null, null))
        assertFalse(PasswordFields.isPassword(null, text(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS), null, null))
        assertFalse(PasswordFields.isPassword(arrayOf("username"), 0, null, null))
        assertFalse(PasswordFields.isPassword(null, InputType.TYPE_CLASS_NUMBER, null, null))
        assertFalse(PasswordFields.isPassword(null, 0, "input", listOf("type" to "text")))
    }

    @Test
    fun `autofill hints and web password inputs are detected`() {
        assertTrue(PasswordFields.isPassword(arrayOf("password"), 0, null, null))
        assertTrue(PasswordFields.isPassword(arrayOf("current-password"), 0, null, null))
        assertTrue(PasswordFields.isPassword(null, 0, "INPUT", listOf("name" to "pw", "Type" to "Password")))
    }

    @Test
    fun `the decrypted password is handed over only once`() {
        DecryptedForFill.set("s3cret", "Bank")
        assertTrue(DecryptedForFill.isReady())
        assertEquals("s3cret", DecryptedForFill.take())
        assertFalse(DecryptedForFill.isReady())
        assertNull(DecryptedForFill.take())
    }

    @Test
    fun `the decrypted password expires`() {
        DecryptedForFill.set("s3cret", "")
        now += DecryptedForFill.WINDOW_SECONDS * 1000L
        assertTrue(DecryptedForFill.isReady())
        now += 1
        assertFalse(DecryptedForFill.isReady())
        assertNull(DecryptedForFill.take())
    }

    @Test
    fun `clearing forgets it`() {
        DecryptedForFill.set("s3cret", "Bank")
        DecryptedForFill.clear()
        assertNull(DecryptedForFill.take())
    }
}
