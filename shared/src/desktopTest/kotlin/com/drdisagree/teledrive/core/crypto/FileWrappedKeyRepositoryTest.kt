package com.drdisagree.teledrive.core.crypto

import com.drdisagree.teledrive.core.files.AppStoragePaths
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileWrappedKeyRepositoryTest {

    private val root = createTempDir()
    private val paths = TestStoragePaths(root)
    private val cipher = IdentityCipher

    private fun keyFile(name: String): File =
        File(File(paths.filesDir, "keys"), "$name.bin")

    @Test
    fun `a key whose file disappeared is not served from cache`() {
        val repository = FileWrappedKeyRepository(paths, cipher)
        val before = repository.getOrCreate(CryptoKeys.TDLIB_DATABASE)
        val file = keyFile(CryptoKeys.TDLIB_DATABASE)
        assertTrue(file.exists())

        assertTrue(file.delete())

        val after = repository.getOrCreate(CryptoKeys.TDLIB_DATABASE)
        assertFalse(
            "a wiped key file must not leave its old key usable in memory",
            before.contentEquals(after)
        )
        assertTrue("getOrCreate must write the replacement key", file.exists())

        val restarted = FileWrappedKeyRepository(paths, cipher)
        assertArrayEquals(after, restarted.get(CryptoKeys.TDLIB_DATABASE))
    }

    @Test
    fun `get does not resurrect a key deleted from under the cache`() {
        val repository = FileWrappedKeyRepository(paths, cipher)
        repository.getOrCreate(CryptoKeys.TDLIB_DATABASE)

        assertTrue(keyFile(CryptoKeys.TDLIB_DATABASE).delete())

        assertNull(repository.get(CryptoKeys.TDLIB_DATABASE))
        assertFalse(keyFile(CryptoKeys.TDLIB_DATABASE).exists())
    }

    private fun createTempDir(): File =
        File.createTempFile("teledrive-keys", "").also {
            it.delete()
            it.mkdirs()
        }

    private object IdentityCipher : CredentialCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext

        override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext
    }

    private class TestStoragePaths(root: File) : AppStoragePaths {
        override val filesDir: File = File(root, "files").apply { mkdirs() }
        override val cacheDir: File = File(root, "cache").apply { mkdirs() }
        override val externalCacheDir: File? = null
        override val externalStorageRoot: File? = null
    }
}
