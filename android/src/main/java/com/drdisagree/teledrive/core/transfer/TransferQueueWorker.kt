package com.drdisagree.teledrive.core.transfer

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppNotifications
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.domain.model.TransferType
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Runs the shared transfer queue drain as expedited work with a dataSync
 * foreground service, so long transfers survive app death.
 */
class TransferQueueWorker(
    appContext: Context,
    params: WorkerParameters,
    private val transferDao: TransferDao,
    private val drainer: TransferQueueDrainer,
    private val settingsRepository: SettingsRepository,
    private val appNotifications: AppNotifications
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (transferDao.nextQueued(1).isEmpty()) return Result.success()
        appNotifications.createChannels()
        runCatching { setForeground(getForegroundInfo()) }

        val result = drainer.drain(
            isStopped = { isStopped },
            onTerminalFailure = { transfer ->
                if (settingsRepository.preferences.first().failureNotifications) {
                    appNotifications.notifyFailure(
                        title = applicationContext.getString(
                            when (transfer.type) {
                                TransferType.UPLOAD,
                                TransferType.BACKUP -> R.string.notification_upload_failed

                                TransferType.DOWNLOAD,
                                TransferType.RESTORE -> R.string.notification_download_failed
                            }
                        ),
                        message = transfer.displayName
                    )
                }
            }
        )
        return when (result) {
            TransferDrainResult.COMPLETED -> Result.success()
            TransferDrainResult.INTERRUPTED -> Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = buildNotification()
        val id = AppNotifications.NOTIFICATION_ID_TRANSFER_QUEUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(applicationContext, AppNotifications.CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notification_transfers_active))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(
                appNotifications.screenIntent(
                    AppNotifications.DESTINATION_TRANSFERS,
                    QUEUE_REQUEST_CODE
                )
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    companion object {
        const val UNIQUE_NAME = "transfer_queue"
        private const val QUEUE_REQUEST_CODE = 3
    }
}
