package com.drdisagree.teledrive.core.transfer

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WorkBackupResumeScheduler(
    private val context: Context
) : BackupResumeScheduler {

    override fun resumeWhen(chargingOnly: Boolean, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<BackupResumeWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(chargingOnly)
                    .setRequiredNetworkType(
                        if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                    )
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BackupResumeWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
