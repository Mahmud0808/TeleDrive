package com.drdisagree.teledrive.core.transfer

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Ensures the queue worker is scheduled; no-op when already running. */
    fun kick(allowMetered: Boolean) {
        val request = OneTimeWorkRequestBuilder<TransferQueueWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED
                    )
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TransferQueueWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Restarts the worker after constraint-relevant settings change. */
    fun rekick(allowMetered: Boolean) {
        val request = OneTimeWorkRequestBuilder<TransferQueueWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED
                    )
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TransferQueueWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
