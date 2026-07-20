package com.javimetallab.papercript

import com.javimetallab.papercript.crypto.Base32
import com.javimetallab.papercript.crypto.PasswordGenerator
import com.javimetallab.papercript.crypto.SecretCodec
import com.javimetallab.papercript.qr.QrCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodecTest {

    @Test
    fun `the QR payload survives a round trip`() {
        val payload = QrCodec.payload("gmail", "K3F92MQX7BTR")
        assertEquals(QrCodec.Parsed("gmail", "K3F92MQX7BTR"), QrCodec.parse(payload))
    }

    @Test
    fun `code separators do not travel inside the QR`() {
        val payload = QrCodec.payload("banco", "K3F9-2MQX-7BTR")
        val parsed = QrCodec.parse(payload)
        assertEquals("K3F92MQX7BTR", parsed?.code)
    }

    @Test
    fun `an empty label is still valid`() {
        val payload = QrCodec.payload("", "K3F92MQX7BTR")
        assertEquals(QrCodec.Parsed("", "K3F92MQX7BTR"), QrCodec.parse(payload))
    }

    @Test
    fun `a label containing the separator does not break the format`() {
        // The label is free user text, so it may contain vertical bars.
        val label = "Gmail | trabajo | antiguo"
        val payload = QrCodec.payload(label, "K3F92MQX7BTR")
        val parsed = QrCodec.parse(payload)
        assertEquals(label, parsed?.label)
        assertEquals("K3F92MQX7BTR", parsed?.code)
    }

    @Test
    fun `a label with accents and spaces is preserved`() {
        val payload = QrCodec.payload("Compañía eléctrica", "K3F92MQX7BTR")
        assertEquals("Compañía eléctrica", QrCodec.parse(payload)?.label)
    }

    @Test
    fun `a foreign QR is rejected`() {
        assertNull(QrCodec.parse("https://example.com"))
        assertNull(QrCodec.parse("WIFI:S:casa;T:WPA;P:secreto;;"))
        assertNull(QrCodec.parse("LBR9|gmail|K3F9"))
        assertNull(QrCodec.parse("LBR1|gmail"))
        assertNull(QrCodec.parse(""))
    }

    @Test
    fun `full cycle of encrypt, embed in QR, read back and decrypt`() {
        val master = "a test master password"
        val password = PasswordGenerator.generate(20)

        val code = SecretCodec.encrypt(password, master)
        val payload = QrCodec.payload(SecretCodec.sanitizeLabel("Gmail"), Base32.normalize(code))

        val parsed = QrCodec.parse(payload)!!
        val result = SecretCodec.decrypt(parsed.code, master)

        assertEquals(SecretCodec.DecryptResult.Success(password), result)
    }

    @Test
    fun `the QR payload stays short`() {
        // This keeps the QR at a low version: fewer modules, so each one is
        // bigger when printed and the symbol is easier to scan.
        val code = SecretCodec.encrypt(PasswordGenerator.generate(20), "maestra")
        val payload = QrCodec.payload("gmail", Base32.normalize(code))
        assertTrue("Payload too long: ${payload.length}", payload.length <= 80)
    }
}
