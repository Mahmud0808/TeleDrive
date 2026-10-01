package com.drdisagree.teledrive.core.crypto

/** Raw keys never touch disk unwrapped; unwrapped copies live in memory only. */
interface WrappedKeyRepository {

    fun getOrCreate(name: String, sizeBytes: Int = 32): ByteArray

    fun wasRecreated(name: String): Boolean

    fun get(name: String): ByteArray?

    fun exists(name: String): Boolean

    fun store(name: String, key: ByteArray)
}
