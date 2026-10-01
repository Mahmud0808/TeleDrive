package com.drdisagree.teledrive.core.crypto

import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.files.AppStoragePaths
import java.io.File
import java.security.SecureRandom

class FileWrappedKeyRepository(
    private val storagePaths: AppStoragePaths,
    private val cipher: CredentialCipher
) : WrappedKeyRepository {

    private val cache = mutableMapOf<String, ByteArray>()
    private val recreated = mutableSetOf<String>()
    private val secureRandom = SecureRandom()

    /**
     * The Keystore cannot unwrap keys of a restored copy; disposable keys are minted again, the
     * content key never is.
     */
    @Synchronized
    override fun getOrCreate(name: String, sizeBytes: Int): ByteArray {
        cache[name]?.let { return it }
        val file = keyFile(name)
        if (file.exists()) {
            val stored = runCatching { cipher.decrypt(file.readBytes()) }.getOrNull()
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
        file.writeBytes(cipher.encrypt(fresh))
        cache[name] = fresh
        return fresh
    }

    /** True when [name] had to be replaced, so whatever it guarded is now junk. */
    @Synchronized
    override fun wasRecreated(name: String): Boolean = name in recreated

    /**
     * Never creates a key: minting one on a decryption path would make existing data unreadable.
     */
    @Synchronized
    override fun get(name: String): ByteArray? {
        cache[name]?.let { return it }
        val file = keyFile(name)
        if (!file.exists()) return null
        return runCatching { cipher.decrypt(file.readBytes()) }
            .onFailure { SafeLog.w(TAG, "Wrapped key $name cannot be unwrapped") }
            .getOrNull()
            ?.also { cache[name] = it }
    }

    @Synchronized
    override fun exists(name: String): Boolean = get(name) != null

    @Synchronized
    override fun store(name: String, key: ByteArray) {
        val file = keyFile(name)
        file.parentFile?.mkdirs()
        file.writeBytes(cipher.encrypt(key))
        cache[name] = key
    }

    private fun keyFile(name: String): File =
        File(File(storagePaths.filesDir, "keys"), "$name.bin")

    private companion object {
        const val TAG = "WrappedKeyRepository"
        val RECREATABLE = setOf(
            CryptoKeys.TDLIB_DATABASE,
            CryptoKeys.THUMBNAIL,
            CryptoKeys.CACHE
        )
    }
}
