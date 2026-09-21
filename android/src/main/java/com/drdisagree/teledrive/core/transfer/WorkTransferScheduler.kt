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
     * Ensures the queue worker is scheduled. A request carries its constraints
     * for life, so work enqueued under Wi-Fi only keeps waiting for Wi-Fi even
     * after the user allows mobile data. Anything not already running is
     * therefore replaced rather than kept, which also clears work restored from
     * another device with constraints this one never chose.
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

    /** Restarts the worker so a waiting queue reacts to a settings change now. */
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
