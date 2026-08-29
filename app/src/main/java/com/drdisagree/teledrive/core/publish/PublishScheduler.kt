package com.drdisagree.teledrive.core.publish

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PublishScheduler(
    private val context: Context
) {

    /**
     * Queues a drain of the publish outbox. Appending rather than keeping means
     * a row marked while the worker is already draining still gets a pass.
     */
    fun kick() {
        val request = OneTimeWorkRequestBuilder<PublishOutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PublishOutboxWorker.UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
