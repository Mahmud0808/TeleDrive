package com.drdisagree.teledrive.core.transfer

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.domain.model.BackupTrigger
import com.drdisagree.teledrive.domain.repository.BackupRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Watches MediaStore while the app process is alive. The WorkManager content
 * trigger still covers the background case, but the system schedules that job
 * and can hold it for minutes; this picks a new shot up straight away whenever
 * the app is running, which is when the user is watching for it.
 */
@Singleton
class MediaStoreWatcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: Provider<BackupRepository>
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            pending?.cancel()
            pending = scope.launch {
                delay(SETTLE_MS)
                scanIfEnabled()
            }
        }
    }

    fun start() {
        runCatching {
            for (collection in WATCHED) {
                context.contentResolver.registerContentObserver(collection, true, observer)
            }
        }.onFailure { SafeLog.w(TAG, "Could not watch MediaStore", it) }
    }

    private suspend fun scanIfEnabled() {
        val prefs = settingsRepository.preferences.first()
        if (!prefs.autoBackupEnabled || !prefs.instantBackupEnabled) return
        SafeLog.d(TAG, "New media seen while running, scanning")
        backupRepository.get().startBackup(BackupTrigger.AUTOMATIC)
    }

    private companion object {
        const val TAG = "MediaStoreWatcher"

        /** One capture writes several rows, so let them settle before scanning. */
        const val SETTLE_MS = 2_000L

        val WATCHED = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
    }
}
