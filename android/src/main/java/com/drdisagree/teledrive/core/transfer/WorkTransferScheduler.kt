package com.drdisagree.teledrive.core.transfer

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager

class WorkTransferScheduler(
    private val context: Context
) : TransferScheduler {

    /**
     * A request keeps its constraints for life, so anything not running is replaced, or Wi-Fi only
     * work would keep waiting after mobile data is allowed.
     */
    override fun kick(allowMetered: Boolean) {
        val workManager = WorkManager.getInstance(context)
        val pending = workManager.getWorkInfosForUniqueWork(TransferQueueWorker.UNIQUE_NAME)
        pending.addListener(
            {
                val running = runCatching { pending.get() }
                    .getOrNull()
                    .orEmpty()
                    .any { it.state == WorkInfo.State.RUNNING }
                enqueue(
                    allowMetered = allowMetered,
                    policy = if (running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
                    expedited = true
                )
            },
            Runnable::run
        )
    }

    override fun rekick(allowMetered: Boolean) {
        enqueue(allowMetered, ExistingWorkPolicy.REPLACE, expedited = false)
    }

    private fun enqueue(
        allowMetered: Boolean,
        policy: ExistingWorkPolicy,
        expedited: Boolean
    ) {
        val request = OneTimeWorkRequestBuilder<TransferQueueWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED
                    )
                    .build()
            )
            .apply {
                if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TransferQueueWorker.UNIQUE_NAME,
            policy,
            request
        )
    }
}
