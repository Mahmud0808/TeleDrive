package com.drdisagree.teledrive.desktop.crypto

import com.drdisagree.teledrive.core.crypto.CredentialCipher
import com.drdisagree.teledrive.core.files.AppStoragePaths
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The master key sits beside the data, so this protects against leaking single files, not a copy of
 * the whole directory.
 */
class LocalKeyCredentialCipher(
    storagePaths: AppStoragePaths
) : CredentialCipher {

    private val random = SecureRandom()
    private val keyFile = File(File(storagePaths.filesDir, "keys"), "master.key")

    /**
     * Read the key on every use instead of caching it: signing out wipes the
     * keys directory, and a cached value would keep encrypting new credentials
     * with a key that no longer exists on disk. The next start would then read
     * the newly generated file and fail to decrypt them.
     */
    @Synchronized
    private fun masterKey(): ByteArray {
        val stored = if (keyFile.exists()) keyFile.readBytes() else null
        if (stored != null && stored.size == KEY_SIZE) return stored
        val fresh = ByteArray(KEY_SIZE).also(random::nextBytes)
        keyFile.parentFile?.mkdirs()
        keyFile.writeBytes(fresh)
        return fresh
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(masterKey(), ALGORITHM),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        val sealed = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(nonce.size + sealed.size).put(nonce).put(sealed).array()
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val nonce = ciphertext.copyOfRange(0, NONCE_SIZE)
        val sealed = ciphertext.copyOfRange(NONCE_SIZE, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(masterKey(), ALGORITHM),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        return cipher.doFinal(sealed)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ALGORITHM = "AES"
        const val KEY_SIZE = 32
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
    }
}
