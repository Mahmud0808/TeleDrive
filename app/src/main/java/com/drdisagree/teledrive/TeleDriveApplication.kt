package com.drdisagree.teledrive

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.drdisagree.teledrive.core.common.AppNotifications
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.proxy.ProxyFailover
import com.drdisagree.teledrive.core.publish.PublishScheduler
import com.drdisagree.teledrive.core.transfer.MaintenanceScheduler
import com.drdisagree.teledrive.core.transfer.MediaStoreWatcher
import com.drdisagree.teledrive.di.appModules
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class TeleDriveApplication : Application(), SingletonImageLoader.Factory {

    private val imageLoader: ImageLoader by inject()

    private val appNotifications: AppNotifications by inject()

    private val maintenanceScheduler: MaintenanceScheduler by inject()

    private val settingsRepository: SettingsRepository by inject()

    private val transferRepository: TransferRepository by inject()

    private val trashRepository: TrashRepository by inject()

    private val mediaStoreWatcher: MediaStoreWatcher by inject()

    private val publishScheduler: PublishScheduler by inject()

    private val proxyFailover: ProxyFailover by inject()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TeleDriveApplication)
            workManagerFactory()
            modules(appModules)
        }
        appNotifications.createChannels()
        mediaStoreWatcher.start()
        proxyFailover.start(applicationScope)
        applicationScope.launch {
            settingsRepository.preferences
                .map { it.debugLogging }
                .distinctUntilChanged()
                .onEach { SafeLog.verbose = it || BuildConfig.DEBUG }
                .launchIn(applicationScope)
            transferRepository.recoverOrphanedTransfers()
            publishScheduler.kick()
            trashRepository.repairTrashTree()
            val prefs = settingsRepository.preferences.first()
            maintenanceScheduler.scheduleAll(
                backupEnabled = prefs.autoBackupEnabled && prefs.backupIntervalHours > 0,
                backupIntervalHours = prefs.backupIntervalHours,
                wifiOnly = prefs.backupWifiOnly,
                chargingOnly = prefs.backupChargingOnly,
                instantBackup = prefs.instantBackupEnabled,
                updateChecks = prefs.updateCheckEnabled
            )
        }
    }
}
