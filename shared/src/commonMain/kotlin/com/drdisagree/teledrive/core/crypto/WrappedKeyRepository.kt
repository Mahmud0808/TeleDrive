package com.drdisagree.teledrive.core.crypto

/**
 * Persists random raw keys wrapped by a platform master key. Raw keys never
 * touch disk unencrypted; unwrapped copies are cached in memory only.
 */
interface WrappedKeyRepository {

    fun getOrCreate(name: String, sizeBytes: Int = 32): ByteArray

    fun wasRecreated(name: String): Boolean

    fun get(name: String): ByteArray?

    fun exists(name: String): Boolean

    fun store(name: String, key: ByteArray)
}
