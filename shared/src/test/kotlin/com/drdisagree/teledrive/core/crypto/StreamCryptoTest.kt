package com.drdisagree.teledrive.core.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StreamCryptoTest {

    private val crypto = StreamCrypto()
    private val key = ByteArray(32) { it.toByte() }
    private val otherKey = ByteArray(32) { (it + 1).toByte() }

    private fun roundTrip(data: ByteArray): ByteArray {
        val encrypted = ByteArrayOutputStream()
        crypto.encryptStream(key, ByteArrayInputStream(data), encrypted)
        val decrypted = ByteArrayOutputStream()
        crypto.decryptStream(key, ByteArrayInputStream(encrypted.toByteArray()), decrypted)
        return decrypted.toByteArray()
    }

    @Test
    fun `stream round trip preserves content`() {
        val data = Random(42).nextBytes(3 * 1024 * 1024 + 17)
        assertArrayEquals(data, roundTrip(data))
    }

    @Test
    fun `empty input round trips`() {
        assertArrayEquals(ByteArray(0), roundTrip(ByteArray(0)))
    }

    @Test
    fun `exact chunk boundary round trips`() {
        val data = Random(7).nextBytes(StreamCrypto.CHUNK_SIZE)
        assertArrayEquals(data, roundTrip(data))
    }

    @Test
    fun `wrong key fails`() {
        val encrypted = ByteArrayOutputStream()
        crypto.encryptStream(key, ByteArrayInputStream(ByteArray(100)), encrypted)
        assertThrows(Exception::class.java) {
            crypto.decryptStream(
                otherKey,
                ByteArrayInputStream(encrypted.toByteArray()),
                ByteArrayOutputStream()
            )
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val encrypted = ByteArrayOutputStream()
        crypto.encryptStream(key, ByteArrayInputStream(ByteArray(1000)), encrypted)
        val bytes = encrypted.toByteArray()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5] + 1).toByte()
        assertThrows(Exception::class.java) {
            crypto.decryptStream(key, ByteArrayInputStream(bytes), ByteArrayOutputStream())
        }
    }

    @Test
    fun `bytes round trip and nonces differ`() {
        val data = "sensitive".toByteArray()
        val sealed1 = crypto.encryptBytes(key, data)
        val sealed2 = crypto.encryptBytes(key, data)
        assertNotEquals(sealed1.toList(), sealed2.toList())
        assertArrayEquals(data, crypto.decryptBytes(key, sealed1))
        assertArrayEquals(data, crypto.decryptBytes(key, sealed2))
    }

    @Test
    fun `dropping trailing chunks is detected`() {
        val data = Random(7).nextBytes(3 * StreamCrypto.CHUNK_SIZE)
        val encrypted = ByteArrayOutputStream()
        crypto.encryptStream(key, ByteArrayInputStream(data), encrypted)

        val full = encrypted.toByteArray()
        val chunkOnDisk = 4 + StreamCrypto.CHUNK_SIZE + 16
        val truncated = full.copyOf(full.size - chunkOnDisk)

        assertThrows(Exception::class.java) {
            crypto.decryptStream(key, ByteArrayInputStream(truncated), ByteArrayOutputStream())
        }
    }

    @Test
    fun `empty stream round trips`() {
        assertArrayEquals(ByteArray(0), roundTrip(ByteArray(0)))
    }

    @Test
    fun `exact chunk multiple round trips`() {
        val data = Random(11).nextBytes(2 * StreamCrypto.CHUNK_SIZE)
        assertArrayEquals(data, roundTrip(data))
    }
}
