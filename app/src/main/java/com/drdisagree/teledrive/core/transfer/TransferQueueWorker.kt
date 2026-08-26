package com.drdisagree.teledrive.core.transfer

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppNotifications
import com.drdisagree.teledrive.core.network.NetworkMonitor
import com.drdisagree.teledrive.core.network.NetworkStatus
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferType
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drains the transfer queue. Runs as expedited work with a dataSync foreground
 * service, so long transfers survive app death. Transient failures are retried
 * with exponential backoff up to the configured retry count; rate limits honor
 * the server-provided delay.
 */
@HiltWorker
class TransferQueueWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transferDao: TransferDao,
    private val fileDao: FileDao,
    private val transferExecutor: TransferExecutor,
    private val backupSessionTracker: BackupSessionTracker,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val appNotifications: AppNotifications
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (transferDao.nextQueued(1).isEmpty()) return Result.success()
        appNotifications.createChannels()
        runCatching { setForeground(getForegroundInfo()) }

        val prefs = settingsRepository.preferences.first()
        val concurrency = prefs.transferConcurrency.coerceIn(1, MAX_CONCURRENCY)
        val claimLock = Mutex()
        var interrupted = false

        coroutineScope {
            List(concurrency) {
                launch {
                    while (true) {
                        if (isStopped) {
                            interrupted = true
                            return@launch
                        }
                        val status = networkMonitor.currentStatus()
                        val blocked = status == NetworkStatus.UNAVAILABLE ||
                                (status == NetworkStatus.METERED && !prefs.allowMeteredTransfers)
                        if (blocked) {
                            interrupted = true
                            return@launch
                        }
                        val next = claimLock.withLock { claimNextQueued() } ?: return@launch
                        runTransfer(next, prefs.transferRetryCount)
                    }
                }
            }.joinAll()
        }
        if (interrupted) {
            withContext(NonCancellable) { transferDao.requeueRunning() }
            return Result.retry()
        }
        return Result.success()
    }

    /**
     * Marks one queued transfer as running before releasing the lock, so every
     * worker in the pool picks a different row and starts as soon as it is free
     * instead of waiting for the rest of a batch to finish.
     */
    private suspend fun claimNextQueued(): String? {
        val next = transferDao.nextQueued(1).firstOrNull() ?: return null
        transferDao.setState(next.id, TransferState.RUNNING, System.currentTimeMillis())
        return next.id
    }

    private suspend fun runTransfer(transferId: String, maxRetries: Int) {
        var attempt = 0
        while (true) {
            val current = transferDao.byId(transferId) ?: return
            if (current.state != TransferState.RUNNING) return

            when (val outcome = transferExecutor.execute(current)) {
                is TransferExecutor.Outcome.Completed -> {
                    refreshSession(current.backupSessionId)
                    return
                }

                is TransferExecutor.Outcome.Paused -> {
                    markState(transferId, TransferState.PAUSED)
                    return
                }

                is TransferExecutor.Outcome.Cancelled -> {
                    markState(transferId, TransferState.CANCELLED)
                    refreshSession(current.backupSessionId)
                    return
                }

                is TransferExecutor.Outcome.Failed -> {
                    attempt++
                    if (attempt > maxRetries) {
                        withContext(NonCancellable) {
                            transferDao.setFailed(
                                transferId,
                                TransferState.FAILED,
                                outcome.message,
                                System.currentTimeMillis()
                            )
                            current.fileId?.let { fileId ->
                                fileDao.setBackupStateIfLocalOnly(fileId, BackupState.FAILED)
                            }
                            if (settingsRepository.preferences.first().failureNotifications) {
                                appNotifications.notifyFailure(
                                    title = applicationContext.getString(
                                        when (current.type) {
                                            TransferType.UPLOAD,
                                            TransferType.BACKUP ->
                                                R.string.notification_upload_failed

                                            TransferType.DOWNLOAD,
                                            TransferType.RESTORE ->
                                                R.string.notification_download_failed
                                        }
                                    ),
                                    message = current.displayName
                                )
                            }
                        }
                        refreshSession(current.backupSessionId)
                        return
                    }
                    val backoffSeconds = outcome.retryAfterSeconds
                        ?: (BASE_BACKOFF_SECONDS shl (attempt - 1)).coerceAtMost(MAX_BACKOFF_SECONDS)
                    delay((backoffSeconds * 1000L).milliseconds)
                }
            }
        }
    }

    private suspend fun refreshSession(sessionId: String?) {
        withContext(NonCancellable) { backupSessionTracker.refresh(sessionId) }
    }

    private suspend fun markState(transferId: String, state: TransferState) {
        withContext(NonCancellable) {
            transferDao.setState(transferId, state, System.currentTimeMillis())
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
        private const val MAX_CONCURRENCY = 6
        private const val QUEUE_REQUEST_CODE = 3
        private const val BASE_BACKOFF_SECONDS = 2
        private const val MAX_BACKOFF_SECONDS = 300
    }
}
