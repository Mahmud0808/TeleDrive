package com.drdisagree.teledrive.core.crypto

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PassphraseKdf {

    private val secureRandom = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH).also(secureRandom::nextBytes)

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val ITERATIONS = 310_000
        private const val SALT_LENGTH = 16
        private const val KEY_BITS = 256
    }
}
