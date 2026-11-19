package com.drdisagree.teledrive.desktop.crypto

import com.drdisagree.teledrive.core.files.AppStoragePaths
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalKeyCredentialCipherTest {

    private val root = createTempDir()
    private val paths = TestStoragePaths(root)

    private val masterKeyFile: File
        get() = File(File(paths.filesDir, "keys"), "master.key")

    @Test
    fun `round trips with the key that is on disk`() {
        val cipher = LocalKeyCredentialCipher(paths)
        val plaintext = "telegram-api-credentials".toByteArray()

        val sealed = cipher.encrypt(plaintext)

        assertArrayEquals(plaintext, LocalKeyCredentialCipher(paths).decrypt(sealed))
    }

    /**
     * Logging out wipes keys/master.key while the app is still running. The
     * next encrypt must use the key that is actually on disk, not a value the
     * cipher cached before the wipe.
     */
    @Test
    fun `a key wiped after the cipher was used is not kept in memory`() {
        val cipher = LocalKeyCredentialCipher(paths)
        cipher.encrypt("before-logout".toByteArray())
        assertTrue(masterKeyFile.exists())

        assertTrue(masterKeyFile.delete())

        val plaintext = "after-logout".toByteArray()
        val sealed = cipher.encrypt(plaintext)

        assertTrue("encrypt must recreate master.key", masterKeyFile.exists())
        assertArrayEquals(plaintext, LocalKeyCredentialCipher(paths).decrypt(sealed))
    }

    @Test
    fun `a replaced key file is picked up without a restart`() {
        val cipher = LocalKeyCredentialCipher(paths)
        val sealedWithOldKey = cipher.encrypt("old".toByteArray())
        val oldKey = masterKeyFile.readBytes()

        masterKeyFile.writeBytes(ByteArray(oldKey.size) { (it + 1).toByte() })

        val sealedWithNewKey = cipher.encrypt("new".toByteArray())
        assertArrayEquals("new".toByteArray(), LocalKeyCredentialCipher(paths).decrypt(sealedWithNewKey))
        assertFalse(
            "the old key must not be reused after the file changed",
            canDecrypt(LocalKeyCredentialCipher(paths), sealedWithOldKey, "old".toByteArray())
        )
    }

    @Test
    fun `a corrupted key file is replaced`() {
        val cipher = LocalKeyCredentialCipher(paths)
        cipher.encrypt("first".toByteArray())
        masterKeyFile.writeBytes(byteArrayOf(1, 2, 3))

        val plaintext = "after-corruption".toByteArray()
        val sealed = cipher.encrypt(plaintext)

        assertArrayEquals(plaintext, LocalKeyCredentialCipher(paths).decrypt(sealed))
    }

    private fun canDecrypt(
        cipher: LocalKeyCredentialCipher,
        sealed: ByteArray,
        expected: ByteArray
    ): Boolean = runCatching { cipher.decrypt(sealed) }
        .map { it.contentEquals(expected) }
        .getOrDefault(false)

    private fun createTempDir(): File =
        File.createTempFile("teledrive-cipher", "").also {
            it.delete()
            it.mkdirs()
        }

    private class TestStoragePaths(root: File) : AppStoragePaths {
        override val filesDir: File = File(root, "files").apply { mkdirs() }
        override val cacheDir: File = File(root, "cache").apply { mkdirs() }
        override val externalCacheDir: File? = null
        override val externalStorageRoot: File? = null
    }
}
