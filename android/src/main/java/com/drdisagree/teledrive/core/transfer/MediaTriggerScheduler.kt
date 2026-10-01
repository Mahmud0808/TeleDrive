package com.drdisagree.teledrive.core.transfer

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import com.drdisagree.teledrive.core.common.SafeLog

/**
 * One fixed job id, so re-arming replaces the previous job; no network or charging requirement,
 * since the upload applies those itself.
 */
class MediaTriggerScheduler(
    private val context: Context
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

        val result =
            runCatching { scheduler.schedule(job) }.getOrDefault(JobScheduler.RESULT_FAILURE)
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
