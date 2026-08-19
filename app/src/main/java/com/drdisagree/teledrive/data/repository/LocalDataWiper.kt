package com.drdisagree.teledrive.data.repository

import android.content.Context
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import com.drdisagree.teledrive.domain.repository.CacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drops everything this device knows about the signed-in drive. Logging out
 * has to leave no index behind: rows point at message ids in a channel the
 * next account cannot read, and a stale index would be merged with whatever
 * the new account has. Files in Telegram are untouched, and signing back in
 * rebuilds the index from the channel.
 */
@Singleton
class LocalDataWiper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: TeleDriveDatabase,
    private val cacheRepository: CacheRepository
) {

    suspend fun wipe() {
        runCatching { cacheRepository.clearAll() }
            .onFailure { SafeLog.w(TAG, "Cache clear failed during wipe", it) }

        runCatching { database.clearAllTables() }
            .onFailure { SafeLog.w(TAG, "Database clear failed during wipe", it) }

        for (name in APP_DIRECTORIES) {
            runCatching { File(context.filesDir, name).deleteRecursively() }
        }
        SafeLog.d(TAG, "Local drive data cleared")
    }

    private companion object {
        const val TAG = "LocalDataWiper"

        /** Import staging and generated previews; the key store is left alone. */
        val APP_DIRECTORIES = listOf("imports", "previews")
    }
}
