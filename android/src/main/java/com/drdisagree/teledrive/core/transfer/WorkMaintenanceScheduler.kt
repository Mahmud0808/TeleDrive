package com.drdisagree.teledrive.core.transfer

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.drdisagree.teledrive.core.update.UpdateCheckWorker

class WorkMaintenanceScheduler(
    private val context: Context,
    private val mediaTriggerScheduler: MediaTriggerScheduler
) : MaintenanceScheduler {

    override fun scheduleUpdateCheck(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(UpdateCheckWorker.UNIQUE_NAME)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            UpdateCheckWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )
    }

    override fun scheduleAll(
        backupEnabled: Boolean,
        backupIntervalHours: Int,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
        instantBackup: Boolean,
        updateChecks: Boolean
    ) {
        val workManager = WorkManager.getInstance(context)

        if (backupEnabled && instantBackup) {
            mediaTriggerScheduler.schedule()
        } else {
            mediaTriggerScheduler.cancel()
        }

        if (backupEnabled) {
            workManager.enqueueUniquePeriodicWork(
                MediaSweepWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<MediaSweepWorker>(
                    SWEEP_INTERVAL_MINUTES,
                    TimeUnit.MINUTES
                ).build()
            )
        } else {
            workManager.cancelUniqueWork(MediaSweepWorker.UNIQUE_NAME)
        }

        if (backupEnabled) {
            val interval = backupIntervalHours.coerceAtLeast(1).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresCharging(chargingOnly)
                .setRequiresBatteryNotLow(true)
                .build()
            workManager.enqueueUniquePeriodicWork(
                ScheduledBackupWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ScheduledBackupWorker>(interval, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
            )
        } else {
            workManager.cancelUniqueWork(ScheduledBackupWorker.UNIQUE_NAME)
        }

        scheduleUpdateCheck(updateChecks)

        workManager.enqueueUniquePeriodicWork(
            TrashCleanupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS).build()
        )

        workManager.enqueueUniquePeriodicWork(
            CacheCleanupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS).build()
        )
    }

    private companion object {
        /** Fast path is the media trigger; this is the floor under it. */
        const val SWEEP_INTERVAL_MINUTES = 60L
    }
}
