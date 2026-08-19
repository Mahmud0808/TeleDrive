package com.drdisagree.teledrive.core.transfer

import android.app.job.JobParameters
import android.app.job.JobService
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.drdisagree.teledrive.core.common.SafeLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Watches MediaStore for new photos and videos through JobScheduler directly.
 *
 * WorkManager was doing this before, but every re-arm churned its job ids and
 * left entries JobScheduler still fired while WorkManager no longer knew them,
 * so triggers were lost. A fixed job id makes re-arming deterministic: the next
 * job replaces the previous one, and the scan itself runs as ordinary work.
 */
@AndroidEntryPoint
class MediaTriggerService : JobService() {

    @Inject
    lateinit var mediaTriggerScheduler: MediaTriggerScheduler

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
