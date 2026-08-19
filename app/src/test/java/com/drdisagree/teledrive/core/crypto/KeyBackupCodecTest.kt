package com.drdisagree.teledrive.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyBackupCodecTest {

    private val codec = KeyBackupCodec(PassphraseKdf(), StreamCrypto())
    private val contentKey = ByteArray(32) { (it * 3).toByte() }

    @Test
    fun `round trip with correct passphrase`() {
        val blob = codec.encode(contentKey, "correct horse".toCharArray(), hint = null)
        assertArrayEquals(contentKey, codec.decode(blob, "correct horse".toCharArray()))
    }

    @Test
    fun `wrong passphrase returns null`() {
        val blob = codec.encode(contentKey, "correct horse".toCharArray(), hint = null)
        assertNull(codec.decode(blob, "wrong".toCharArray()))
    }

    @Test
    fun `hint is readable without passphrase`() {
        val blob = codec.encode(contentKey, "pw".toCharArray(), hint = "favorite color")
        assertEquals("favorite color", codec.readInfo(blob)?.hint)
    }

    @Test
    fun `missing hint reads as null`() {
        val blob = codec.encode(contentKey, "pw".toCharArray(), hint = null)
        assertNull(codec.readInfo(blob)?.hint)
    }

    @Test
    fun `decode with hint still works`() {
        val blob = codec.encode(contentKey, "pw".toCharArray(), hint = "some hint")
        assertArrayEquals(contentKey, codec.decode(blob, "pw".toCharArray()))
    }

    @Test
    fun `garbage blob returns null`() {
        assertNull(codec.decode(ByteArray(64) { 1 }, "pw".toCharArray()))
    }
}
