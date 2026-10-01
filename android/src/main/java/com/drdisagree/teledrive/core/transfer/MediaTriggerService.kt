package com.drdisagree.teledrive.core.transfer

import android.app.job.JobParameters
import android.app.job.JobService
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.drdisagree.teledrive.core.common.SafeLog
import org.koin.android.ext.android.inject

/**
 * JobScheduler directly with a fixed job id: WorkManager churns job ids on re-arm and loses
 * triggers.
 */
class MediaTriggerService : JobService() {

    private val mediaTriggerScheduler: MediaTriggerScheduler by inject()

    override fun onStartJob(params: JobParameters?): Boolean {
        SafeLog.d(TAG, "Media change reported")
        mediaTriggerScheduler.schedule()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            MediaWatchWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MediaWatchWorker>().build()
        )
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    private companion object {
        const val TAG = "MediaTriggerService"
    }
}
