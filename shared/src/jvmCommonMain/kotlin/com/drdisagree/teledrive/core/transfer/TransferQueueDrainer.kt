package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.network.NetworkMonitor
import com.drdisagree.teledrive.core.network.NetworkStatus
import com.drdisagree.teledrive.core.power.PowerMonitor
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferType
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

/** Transient failures retry with exponential backoff; rate limits wait the server-given delay. */
class TransferQueueDrainer(
    private val transferDao: TransferDao,
    private val fileDao: FileDao,
    private val transferExecutor: TransferExecutor,
    private val backupSessionTracker: BackupSessionTracker,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val powerMonitor: PowerMonitor,
    private val backupResumeScheduler: BackupResumeScheduler
) {

    suspend fun drain(
        isStopped: () -> Boolean,
        onTerminalFailure: suspend (TransferEntity) -> Unit
    ): TransferDrainResult {
        val claimLock = Mutex()
        var interrupted = false
        var backupsHeld = false

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
                        val hold = BackupGate.hold(prefs, status, powerMonitor.isCharging())
                        val next = claimLock.withLock { claimNextQueued(includeBackups = hold == null) }
                        if (next == null) {
                            if (hold != null && transferDao.hasQueued(TransferType.BACKUP)) {
                                backupsHeld = true
                            }
                            return@launch
                        }
                        runTransfer(next, prefs.transferRetryCount, onTerminalFailure)
                    }
                }
            }.joinAll()
        }
        if (backupsHeld) scheduleResume()
        if (interrupted) {
            withContext(NonCancellable) { transferDao.requeueRunning() }
            return TransferDrainResult.INTERRUPTED
        }
        return TransferDrainResult.COMPLETED
    }

    suspend fun hasRunnableWork(): Boolean {
        val prefs = settingsRepository.preferences.first()
        val hold = currentHold(prefs)
        if (nextClaimable(includeBackups = hold == null) != null) return true
        if (hold != null && transferDao.hasQueued(TransferType.BACKUP)) scheduleResume()
        return false
    }

    private fun currentHold(prefs: UserPreferences) =
        BackupGate.hold(prefs, networkMonitor.currentStatus(), powerMonitor.isCharging())

    private suspend fun scheduleResume() {
        val prefs = settingsRepository.preferences.first()
        backupResumeScheduler.resumeWhen(prefs.backupChargingOnly, prefs.backupWifiOnly)
    }

    private fun concurrencyOf(prefs: UserPreferences): Int =
        prefs.transferConcurrency.coerceIn(1, MAX_CONCURRENCY)

    /**
     * Raising the setting wakes a parked slot at once; returns false once the queue drains so the
     * slot can finish.
     */
    private suspend fun awaitSlot(slot: Int): Boolean {
        val widened = withTimeoutOrNull(SLOT_WAIT_MS.milliseconds) {
            settingsRepository.preferences.first { slot < concurrencyOf(it) }
        }
        if (widened != null) return true
        val hold = currentHold(settingsRepository.preferences.first())
        return nextClaimable(includeBackups = hold == null) != null
    }

    /** Marked running before the lock is released, so every slot picks a different row. */
    private suspend fun nextClaimable(includeBackups: Boolean): TransferEntity? =
        if (includeBackups) {
            transferDao.nextQueued(1).firstOrNull()
        } else {
            transferDao.nextQueuedExcept(TransferType.BACKUP, 1).firstOrNull()
        }

    private suspend fun claimNextQueued(includeBackups: Boolean): String? {
        val next = nextClaimable(includeBackups) ?: return null
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
                is TransferOutcome.Completed -> {
                    refreshSession(current.backupSessionId)
                    return
                }

                is TransferOutcome.Paused -> {
                    markState(transferId, TransferState.PAUSED)
                    return
                }

                is TransferOutcome.Canceled -> {
                    markState(transferId, TransferState.CANCELLED)
                    refreshSession(current.backupSessionId)
                    return
                }

                is TransferOutcome.Failed -> {
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
