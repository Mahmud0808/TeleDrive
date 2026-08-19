package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Rewrites the caption manifest of already-uploaded files so organisational
 * state (name, folder, favorite, hidden, archived, trash) lives in Telegram
 * and survives a local wipe. Local rows are the fast path; the caption is the
 * durable copy.
 */
@Singleton
class FileManifestPublisher @Inject constructor(
    private val fileDao: FileDao,
    private val telegramClient: TelegramClient,
    private val manifestCodec: ManifestCodec,
    private val folderPathResolver: FolderPathResolver
) {

    suspend fun publish(fileId: String): AppResult<Unit> {
        val entity = fileDao.byId(fileId) ?: return AppResult.Failure(AppError.NotFound)
        return publish(entity)
    }

    suspend fun publishAll(fileIds: List<String>): AppResult<Unit> {
        if (fileIds.isEmpty()) return AppResult.Success(Unit)
        val failure = AtomicReference<AppResult.Failure?>(null)
        val gate = Semaphore(PUBLISH_CONCURRENCY)
        for (chunk in fileIds.chunked(SQL_BATCH)) {
            val entities = fileDao.byIds(chunk)
            coroutineScope {
                entities.map { entity ->
                    async {
                        gate.withPermit {
                            val result = publish(entity)
                            if (result is AppResult.Failure) failure.set(result)
                        }
                    }
                }.awaitAll()
            }
        }
        return failure.get() ?: AppResult.Success(Unit)
    }

    suspend fun publish(entity: FileEntity): AppResult<Unit> {
        val chatId = entity.chatId ?: return AppResult.Success(Unit)
        val messageId = entity.messageId ?: return AppResult.Success(Unit)
        val manifest = RemoteFileManifest(
            fileId = entity.id,
            name = entity.name,
            folderPath = folderPathResolver.pathOf(entity.folderId ?: entity.preTrashFolderId),
            folderId = entity.folderId ?: entity.preTrashFolderId,
            mimeType = entity.mimeType,
            sizeBytes = entity.sizeBytes,
            contentHash = entity.contentHash,
            hidden = entity.isHidden,
            archived = entity.isArchived,
            favorite = entity.isFavorite,
            trashedAt = entity.trashedAt,
            encrypted = entity.isEncrypted,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
            width = entity.width,
            height = entity.height,
            durationMs = entity.durationMs
        )
        return try {
            telegramClient.editCaption(
                chatId,
                messageId,
                manifestCodec.encode(manifest, entity.isEncrypted)
            )
            AppResult.Success(Unit)
        } catch (e: TelegramException) {
            AppResult.Failure(
                if (e.isRateLimit) AppError.RateLimited(e.retryAfterSeconds ?: 0)
                else AppError.TelegramError(e.code, e.message)
            )
        }
    }

    private companion object {
        const val SQL_BATCH = 500
        const val PUBLISH_CONCURRENCY = 4
    }
}
