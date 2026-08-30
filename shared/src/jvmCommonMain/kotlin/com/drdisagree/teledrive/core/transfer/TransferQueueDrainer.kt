package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.network.NetworkMonitor
import com.drdisagree.teledrive.core.network.NetworkStatus
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.UserPreferences
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlin.time.Duration.Companion.milliseconds
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

/**
 * Drains the transfer queue with bounded concurrency. Transient failures are
 * retried with exponential backoff up to the configured retry count; rate
 * limits honor the server-provided delay. Platform schedulers wrap this and
 * add whatever their environment needs, like a foreground service or
 * failure notifications.
 */
class TransferQueueDrainer(
    private val transferDao: TransferDao,
    private val fileDao: FileDao,
    private val transferExecutor: TransferExecutor,
    private val backupSessionTracker: BackupSessionTracker,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor
) {

    suspend fun drain(
        isStopped: () -> Boolean,
        onTerminalFailure: suspend (TransferEntity) -> Unit
    ): TransferDrainResult {
        val claimLock = Mutex()
        var interrupted = false

        coroutineScope {
            List(MAX_CONCURRENCY) { slot ->
                launch {
                    while (true) {
                        if (isStopped()) {
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
                        runTransfer(next, prefs.transferRetryCount, onTerminalFailure)
                    }
                }
            }.joinAll()
        }
        if (interrupted) {
            withContext(NonCancellable) { transferDao.requeueRunning() }
            return TransferDrainResult.INTERRUPTED
        }
        return TransferDrainResult.COMPLETED
    }

    private fun concurrencyOf(prefs: UserPreferences): Int =
        prefs.transferConcurrency.coerceIn(1, MAX_CONCURRENCY)

    /**
     * Parks a slot the current setting has no room for. Raising the setting
     * wakes it immediately, so a change applies to the backup already running
     * instead of only to the next one. Returns false once the queue has
     * drained, which lets a parked slot finish rather than hold the drain open.
     */
    private suspend fun awaitSlot(slot: Int): Boolean {
        val widened = withTimeoutOrNull(SLOT_WAIT_MS.milliseconds) {
            settingsRepository.preferences.first { slot < concurrencyOf(it) }
        }
        return widened != null || transferDao.nextQueued(1).isNotEmpty()
    }

    /**
     * Marks one queued transfer as running before releasing the lock, so every
     * slot in the pool picks a different row and starts as soon as it is free
     * instead of waiting for the rest of a batch to finish.
     */
    private suspend fun claimNextQueued(): String? {
        val next = transferDao.nextQueued(1).firstOrNull() ?: return null
        transferDao.setState(next.id, TransferState.RUNNING, System.currentTimeMillis())
        return next.id
    }

    private suspend fun runTransfer(
        transferId: String,
        maxRetries: Int,
        onTerminalFailure: suspend (TransferEntity) -> Unit
    ) {
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

                is TransferExecutor.Outcome.Canceled -> {
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
                            onTerminalFailure(current)
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

    private companion object {
        const val MAX_CONCURRENCY = 6
        const val SLOT_WAIT_MS = 5_000L
        const val BASE_BACKOFF_SECONDS = 2
        const val MAX_BACKOFF_SECONDS = 300
    }
}
