package com.drdisagree.teledrive.core.publish

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.FolderDao
import com.drdisagree.teledrive.data.repository.FileManifestPublisher
import com.drdisagree.teledrive.data.repository.FolderStateSynchronizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.data.local.dao.PendingDeleteDao

/**
 * Drains the publish outbox: organizational state is written to the database
 * first so the app stays responsive, and this worker mirrors it into Telegram
 * afterward. Rows keep their flag until the caption is accepted, so a failed
 * edit is retried instead of being reverted by the next sync.
 */
@HiltWorker
class PublishOutboxWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fileDao: FileDao,
    private val folderDao: FolderDao,
    private val pendingDeleteDao: PendingDeleteDao,
    private val telegramClient: TelegramClient,
    private val manifestPublisher: FileManifestPublisher,
    private val folderStateSynchronizer: FolderStateSynchronizer
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!replayDeletes()) return Result.retry()

        while (true) {
            if (isStopped) return Result.retry()
            val batch = fileDao.pendingPublish(BATCH_SIZE)
            if (batch.isEmpty()) break

            for (entity in batch) {
                if (isStopped) return Result.retry()
                when (val result = manifestPublisher.publish(entity)) {
                    is AppResult.Success -> fileDao.clearPendingPublish(entity.id)
                    is AppResult.Failure -> {
                        if (!isPermanent(result.error)) return Result.retry()
                        SafeLog.w(TAG, "Dropping unpublishable manifest: ${result.error}")
                        fileDao.clearPendingPublish(entity.id)
                    }
                }
            }
        }

        if (folderDao.pendingPublishCount() > 0) {
            val pushed = runCatching { folderStateSynchronizer.push() }
                .onFailure { SafeLog.w(TAG, "Folder state push failed", it) }
                .isSuccess
            if (!pushed) return Result.retry()
            folderDao.clearPendingPublish()
        }
        return Result.success()
    }

    /**
     * Finishes permanent deletes that were interrupted or rejected. Removing a
     * message that is already gone is accepted by Telegram, so replaying after
     * a crash costs nothing.
     */
    private suspend fun replayDeletes(): Boolean {
        while (true) {
            if (isStopped) return false
            val batch = pendingDeleteDao.oldest(BATCH_SIZE)
            if (batch.isEmpty()) return true

            for ((chatId, pending) in batch.groupBy { it.chatId }) {
                val messageIds = pending.map { it.messageId }
                try {
                    telegramClient.deleteMessages(chatId, messageIds)
                } catch (e: TelegramException) {
                    if (e.code !in PERMANENT_CODES) {
                        SafeLog.w(TAG, "Replaying ${messageIds.size} deletes failed", e)
                        return false
                    }
                    SafeLog.w(TAG, "Dropping unrepeatable delete: ${e.message}")
                }
                fileDao.deleteByIds(pending.map { it.fileId })
                pendingDeleteDao.clear(chatId, messageIds)
            }
        }
    }

    /** A rejected edit never succeeds on retry, unlike a rate limit or an outage. */
    private fun isPermanent(error: AppError): Boolean =
        error is AppError.TelegramError && error.code in PERMANENT_CODES

    companion object {
        const val UNIQUE_NAME = "publish-outbox"
        private const val TAG = "PublishOutbox"
        private const val BATCH_SIZE = 100
        private val PERMANENT_CODES = 400..499
    }
}
