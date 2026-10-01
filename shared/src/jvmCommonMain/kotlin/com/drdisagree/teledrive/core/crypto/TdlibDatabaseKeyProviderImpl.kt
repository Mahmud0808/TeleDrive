package com.drdisagree.teledrive.core.crypto

import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.telegram.TdlibDatabaseKeyProvider
import java.io.File

class TdlibDatabaseKeyProviderImpl(
    private val storagePaths: AppStoragePaths,
    private val wrappedKeyRepository: WrappedKeyRepository
) : TdlibDatabaseKeyProvider {

    /**
     * A restored session database cannot be opened once its key is minted again, and TDLib fails
     * instead of starting fresh.
     */
    override fun databaseKey(): ByteArray {
        val key = wrappedKeyRepository.getOrCreate(CryptoKeys.TDLIB_DATABASE)
        if (wrappedKeyRepository.wasRecreated(CryptoKeys.TDLIB_DATABASE)) {
            val database = File(storagePaths.filesDir, TDLIB_DIR)
            if (database.exists()) {
                SafeLog.w(TAG, "Dropping a session database this device cannot open")
                database.deleteRecursively()
            }
        }
        return key
    }

    private companion object {
        const val TAG = "TdlibDatabaseKey"
        const val TDLIB_DIR = "tdlib"
    }
}
