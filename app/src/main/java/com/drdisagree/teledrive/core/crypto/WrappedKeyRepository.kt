package com.drdisagree.teledrive.core.crypto

import android.content.Context
import com.drdisagree.teledrive.core.common.SafeLog
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
    private val recreated = mutableSetOf<String>()
    private val secureRandom = SecureRandom()

    /**
     * The Keystore refuses to unwrap a key it did not create, which is what a
     * file-level restore onto another device produces. Keys guarding disposable
     * data are minted again; the content key never is, because a fresh one
     * would quietly make every encrypted upload unreadable.
     */
    @Synchronized
    fun getOrCreate(name: String, sizeBytes: Int = 32): ByteArray {
        cache[name]?.let { return it }
        val file = keyFile(name)
        if (file.exists()) {
            val stored = runCatching { keystoreManager.decrypt(file.readBytes()) }.getOrNull()
            if (stored != null) {
                cache[name] = stored
                return stored
            }
            SafeLog.w(TAG, "Wrapped key $name cannot be unwrapped on this device")
            if (name !in RECREATABLE) throw KeyUnavailableException(name)
            file.delete()
            recreated += name
        }
        val fresh = ByteArray(sizeBytes).also(secureRandom::nextBytes)
        file.parentFile?.mkdirs()
        file.writeBytes(keystoreManager.encrypt(fresh))
        cache[name] = fresh
        return fresh
    }

    /** True when [name] had to be replaced, so whatever it guarded is now junk. */
    @Synchronized
    fun wasRecreated(name: String): Boolean = name in recreated

    /**
     * Reads a key without ever creating one. Decryption paths must use this:
     * minting a fresh key there would silently make existing data unreadable.
     */
    @Synchronized
    fun get(name: String): ByteArray? {
        cache[name]?.let { return it }
        val file = keyFile(name)
        if (!file.exists()) return null
        return runCatching { keystoreManager.decrypt(file.readBytes()) }
            .onFailure { SafeLog.w(TAG, "Wrapped key $name cannot be unwrapped") }
            .getOrNull()
            ?.also { cache[name] = it }
    }

    /**
     * Whether the key is present and this device can actually unwrap it.
     */
    @Synchronized
    fun exists(name: String): Boolean = get(name) != null

    @Synchronized
    fun store(name: String, key: ByteArray) {
        val file = keyFile(name)
        file.parentFile?.mkdirs()
        file.writeBytes(keystoreManager.encrypt(key))
        cache[name] = key
    }

    private fun keyFile(name: String): File =
        File(File(context.filesDir, "keys"), "$name.bin")

    private companion object {
        const val TAG = "WrappedKeyRepository"
        val RECREATABLE = setOf(
            CryptoKeys.TDLIB_DATABASE,
            CryptoKeys.THUMBNAIL,
            CryptoKeys.CACHE
        )
    }
}

/** Raised for a key that guards data a new key would destroy. */
class KeyUnavailableException(name: String) :
    Exception("Encryption key $name cannot be read on this device")
