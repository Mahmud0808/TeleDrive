package com.drdisagree.teledrive.core.crypto

/** Seals small secrets with a key only this device can use. */
interface CredentialCipher {

    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray): ByteArray
}
