package com.drdisagree.teledrive

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.drdisagree.teledrive.core.common.AppNotifications
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.transfer.MaintenanceScheduler
import com.drdisagree.teledrive.core.transfer.MediaStoreWatcher
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class TeleDriveApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoaderProvider: Provider<ImageLoader>

    @Inject
    lateinit var appNotifications: AppNotifications

    @Inject
    lateinit var maintenanceScheduler: MaintenanceScheduler

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var transferRepository: TransferRepository

    @Inject
    lateinit var trashRepository: TrashRepository

    @Inject
    lateinit var mediaStoreWatcher: MediaStoreWatcher

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        imageLoaderProvider.get()

    override fun onCreate() {
        super.onCreate()
        appNotifications.createChannels()
        mediaStoreWatcher.start()
        applicationScope.launch {
            settingsRepository.preferences
                .map { it.debugLogging }
                .distinctUntilChanged()
                .onEach { SafeLog.verbose = it || BuildConfig.DEBUG }
                .launchIn(applicationScope)
            transferRepository.recoverOrphanedTransfers()
            trashRepository.repairTrashTree()
            val prefs = settingsRepository.preferences.first()
            maintenanceScheduler.scheduleAll(
                backupEnabled = prefs.autoBackupEnabled && prefs.backupIntervalHours > 0,
                backupIntervalHours = prefs.backupIntervalHours,
                wifiOnly = prefs.backupWifiOnly,
                chargingOnly = prefs.backupChargingOnly,
                instantBackup = prefs.instantBackupEnabled
            )
        }
    }
}
