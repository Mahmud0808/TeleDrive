package com.drdisagree.teledrive.core.crypto

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists random raw keys wrapped by the Keystore master key. Raw keys never
 * touch disk unencrypted; unwrapped copies are cached in memory only.
 */
@Singleton
class WrappedKeyRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager
) {

    private val cache = mutableMapOf<String, ByteArray>()
    private val secureRandom = SecureRandom()

    @Synchronized
    fun getOrCreate(name: String, sizeBytes: Int = 32): ByteArray {
        cache[name]?.let { return it }
        val file = keyFile(name)
        val key = if (file.exists()) {
            keystoreManager.decrypt(file.readBytes())
        } else {
            val fresh = ByteArray(sizeBytes).also(secureRandom::nextBytes)
            file.parentFile?.mkdirs()
            file.writeBytes(keystoreManager.encrypt(fresh))
            fresh
        }
        cache[name] = key
        return key
    }

    /**
     * Reads a key without ever creating one. Decryption paths must use this:
     * minting a fresh key there would silently make existing data unreadable.
     */
    @Synchronized
    fun get(name: String): ByteArray? {
        cache[name]?.let { return it }
        val file = keyFile(name)
        if (!file.exists()) return null
        return keystoreManager.decrypt(file.readBytes()).also { cache[name] = it }
    }

    fun exists(name: String): Boolean = cache.containsKey(name) || keyFile(name).exists()

    @Synchronized
    fun store(name: String, key: ByteArray) {
        val file = keyFile(name)
        file.parentFile?.mkdirs()
        file.writeBytes(keystoreManager.encrypt(key))
        cache[name] = key
    }

    private fun keyFile(name: String): File =
        File(File(context.filesDir, "keys"), "$name.bin")
}
