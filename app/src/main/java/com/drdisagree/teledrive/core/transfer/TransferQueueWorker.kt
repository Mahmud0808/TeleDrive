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
import com.drdisagree.teledrive.core.network.NetworkMonitor
import com.drdisagree.teledrive.core.network.NetworkStatus
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferType
import com.drdisagree.teledrive.domain.model.UserPreferences
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drains the transfer queue. Runs as expedited work with a dataSync foreground
 * service, so long transfers survive app death. Transient failures are retried
 * with exponential backoff up to the configured retry count; rate limits honor
 * the server-provided delay.
 */
class TransferQueueWorker(
    appContext: Context,
    params: WorkerParameters,
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

        val claimLock = Mutex()
        var interrupted = false

        coroutineScope {
            List(MAX_CONCURRENCY) { slot ->
                launch {
                    while (true) {
                        if (isStopped) {
                            interrupted = true
                            return@launch
                        }
                        val prefs = settingsRepository.preferences.first()
                        if (slot >= concurrencyOf(prefs)) {
                            if (!awaitSlot(slot)) return@launch
                            continue
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

    private fun concurrencyOf(prefs: UserPreferences): Int =
        prefs.transferConcurrency.coerceIn(1, MAX_CONCURRENCY)

    /**
     * Parks a slot the current setting has no room for. Raising the setting
     * wakes it immediately, so a change applies to the backup already running
     * instead of only to the next one. Returns false once the queue has drained,
     * which is what lets a parked slot finish rather than hold the worker open.
     */
    private suspend fun awaitSlot(slot: Int): Boolean {
        val widened = withTimeoutOrNull(SLOT_WAIT_MS.milliseconds) {
            settingsRepository.preferences.first { slot < concurrencyOf(it) }
        }
        return widened != null || transferDao.nextQueued(1).isNotEmpty()
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
        private const val SLOT_WAIT_MS = 5_000L
        private const val QUEUE_REQUEST_CODE = 3
        private const val BASE_BACKOFF_SECONDS = 2
        private const val MAX_BACKOFF_SECONDS = 300
    }
}
