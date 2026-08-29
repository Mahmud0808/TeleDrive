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
 * AES-GCM under a master key stored beside the data, for platforms without an
 * OS keystore. Protects against leaking individual files, not a full copy of
 * the directory; those platforms should grow a real keystore backend.
 */
class LocalKeyCredentialCipher(
    storagePaths: AppStoragePaths
) : CredentialCipher {

    private val random = SecureRandom()

    private val masterKey: ByteArray by lazy {
        val file = File(File(storagePaths.filesDir, "keys"), "master.key")
        if (file.exists()) {
            file.readBytes()
        } else {
            val fresh = ByteArray(KEY_SIZE).also(random::nextBytes)
            file.parentFile?.mkdirs()
            file.writeBytes(fresh)
            fresh
        }
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(masterKey, ALGORITHM),
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
            SecretKeySpec(masterKey, ALGORITHM),
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
