package com.drdisagree.teledrive.core.crypto

import android.content.Context
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.telegram.TdlibDatabaseKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TdlibDatabaseKeyProviderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wrappedKeyRepository: WrappedKeyRepository
) : TdlibDatabaseKeyProvider {

    /**
     * A restored copy of the session database is unopenable once its key has
     * been minted again, and TDLib fails rather than starting fresh, so the
     * stale database goes with the stale key.
     */
    override fun databaseKey(): ByteArray {
        val key = wrappedKeyRepository.getOrCreate(CryptoKeys.TDLIB_DATABASE)
        if (wrappedKeyRepository.wasRecreated(CryptoKeys.TDLIB_DATABASE)) {
            val database = File(context.filesDir, TDLIB_DIR)
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
