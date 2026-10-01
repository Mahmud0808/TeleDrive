package com.drdisagree.teledrive.core.crypto

interface CredentialCipher {

    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray): ByteArray
}
