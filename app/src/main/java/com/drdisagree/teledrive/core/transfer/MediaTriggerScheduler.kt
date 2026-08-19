package com.drdisagree.teledrive.core.transfer

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import com.drdisagree.teledrive.core.common.SafeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms the MediaStore content trigger under one fixed job id, so re-arming
 * replaces the previous job instead of leaving orphans behind. The job carries
 * no network or charging requirement: noticing a new photo has to happen even
 * on mobile data, and the upload it queues applies those preferences itself.
 */
@Singleton
class MediaTriggerScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun schedule() {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, MediaTriggerService::class.java)
        )
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                )
            )
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                )
            )
            .setTriggerContentUpdateDelay(UPDATE_DELAY_MS)
            .setTriggerContentMaxDelay(MAX_DELAY_MS)
            .build()

        val result = runCatching { scheduler.schedule(job) }.getOrDefault(JobScheduler.RESULT_FAILURE)
        if (result != JobScheduler.RESULT_SUCCESS) {
            SafeLog.w(TAG, "Could not arm the media trigger")
        }
    }

    fun cancel() {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }

    private companion object {
        const val TAG = "MediaTriggerScheduler"
        const val JOB_ID = 4711
        const val UPDATE_DELAY_MS = 3_000L
        const val MAX_DELAY_MS = 60_000L
    }
}
