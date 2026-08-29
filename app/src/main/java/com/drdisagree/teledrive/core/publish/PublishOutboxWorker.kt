package com.drdisagree.teledrive.core.publish

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Runs the shared publish outbox drain as background work. */
class PublishOutboxWorker(
    appContext: Context,
    params: WorkerParameters,
    private val drainer: PublishOutboxDrainer
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        if (drainer.drain(isStopped = { isStopped })) Result.success() else Result.retry()

    companion object {
        const val UNIQUE_NAME = "publish-outbox"
    }
}
