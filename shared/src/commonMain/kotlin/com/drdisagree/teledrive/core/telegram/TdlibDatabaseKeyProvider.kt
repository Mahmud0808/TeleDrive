package com.drdisagree.teledrive.core.telegram

/**
 * Supplies the key that encrypts TDLib's local database. Implemented by the
 * crypto layer (random key wrapped by an Android Keystore key).
 */
interface TdlibDatabaseKeyProvider {
    fun databaseKey(): ByteArray
}
