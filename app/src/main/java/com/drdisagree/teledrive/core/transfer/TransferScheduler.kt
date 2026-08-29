package com.drdisagree.teledrive.core.transfer

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.core.content.edit

class TransferScheduler(
    private val context: Context
) {

    private val state =
        context.getSharedPreferences(SCHEDULER_PREFS, Context.MODE_PRIVATE)

    /**
     * Ensures the queue worker is scheduled. A request carries its constraints
     * for life, so queued work enqueued under Wi-Fi only would keep waiting for
     * Wi-Fi after the user allows mobile data. The constraint that was used is
     * remembered and the work is replaced whenever the rule changes.
     */
    fun kick(allowMetered: Boolean) {
        val policy = if (state.getBoolean(KEY_ALLOW_METERED, false) == allowMetered &&
            state.contains(KEY_ALLOW_METERED)
        ) {
            ExistingWorkPolicy.KEEP
        } else {
            ExistingWorkPolicy.REPLACE
        }
        enqueue(allowMetered, policy, expedited = true)
    }

    /** Restarts the worker so a waiting queue reacts to a settings change now. */
    fun rekick(allowMetered: Boolean) {
        enqueue(allowMetered, ExistingWorkPolicy.REPLACE, expedited = false)
    }

    private fun enqueue(
        allowMetered: Boolean,
        policy: ExistingWorkPolicy,
        expedited: Boolean
    ) {
        state.edit { putBoolean(KEY_ALLOW_METERED, allowMetered) }
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

    private companion object {
        const val SCHEDULER_PREFS = "transfer-scheduler"
        const val KEY_ALLOW_METERED = "allow_metered"
    }
}
