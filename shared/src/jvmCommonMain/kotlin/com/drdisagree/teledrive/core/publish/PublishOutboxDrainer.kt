package com.drdisagree.teledrive.core.publish

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.FolderDao
import com.drdisagree.teledrive.data.local.dao.PendingDeleteDao
import com.drdisagree.teledrive.data.repository.FileManifestPublisher
import com.drdisagree.teledrive.data.repository.FolderStateSynchronizer
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Drains the publish outbox: organizational state is written to the database
 * first so the app stays responsive, and this drain mirrors it into Telegram
 * afterward. Rows keep their flag until the caption is accepted, so a failed
 * edit is retried instead of being reverted by the next sync.
 */
class PublishOutboxDrainer(
    private val fileDao: FileDao,
    private val folderDao: FolderDao,
    private val pendingDeleteDao: PendingDeleteDao,
    private val telegramClient: TelegramClient,
    private val manifestPublisher: FileManifestPublisher,
    private val folderStateSynchronizer: FolderStateSynchronizer
) {

    suspend fun drain(isStopped: () -> Boolean): Boolean {
        if (!replayDeletes(isStopped)) return false

        while (true) {
            if (isStopped()) return false
            val batch = fileDao.pendingPublish(BATCH_SIZE)
            if (batch.isEmpty()) break

            var index = 0
            while (index < batch.size) {
                val entity = batch[index++]
                if (isStopped()) return false
                when (val result = manifestPublisher.publish(entity)) {
                    is AppResult.Success -> fileDao.clearPendingPublish(entity.id)
                    is AppResult.Failure -> {
                        val error = result.error
                        if (error is AppError.RateLimited) {
                            if (error.retryAfterSeconds > INLINE_WAIT_LIMIT) return false
                            delay(((error.retryAfterSeconds + 1) * 1000L).milliseconds)
                            index--
                            continue
                        }
                        if (!isPermanent(error)) return false
                        SafeLog.w(TAG, "Dropping unpublishable manifest: $error")
                        fileDao.clearPendingPublish(entity.id)
                    }
                }
            }
        }

        if (folderDao.pendingPublishCount() > 0) {
            val pushed = runCatching { folderStateSynchronizer.push() }
                .onFailure { SafeLog.w(TAG, "Folder state push failed", it) }
                .isSuccess
            if (!pushed) return false
            folderDao.clearPendingPublish()
        }
        return true
    }

    /**
     * Finishes permanent deletes that were interrupted or rejected. Removing a
     * message that is already gone is accepted by Telegram, so replaying after
     * a crash costs nothing.
     */
    private suspend fun replayDeletes(isStopped: () -> Boolean): Boolean {
        while (true) {
            if (isStopped()) return false
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

    private companion object {
        const val TAG = "PublishOutbox"
        const val BATCH_SIZE = 100
        const val INLINE_WAIT_LIMIT = 60
        val PERMANENT_CODES = 400..499
    }
}
