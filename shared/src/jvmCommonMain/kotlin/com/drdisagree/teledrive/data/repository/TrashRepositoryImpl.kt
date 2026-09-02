package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.crypto.SecureFileDeleter
import com.drdisagree.teledrive.core.publish.PublishScheduler
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.data.local.dao.BackupDao
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.FilePartDao
import com.drdisagree.teledrive.data.local.dao.FolderDao
import com.drdisagree.teledrive.data.local.dao.PendingDeleteDao
import com.drdisagree.teledrive.data.local.dao.ThumbnailDao
import com.drdisagree.teledrive.data.local.entity.PendingDeleteEntity
import com.drdisagree.teledrive.data.mapper.toDomain
import com.drdisagree.teledrive.domain.model.TrashItem
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TrashRepositoryImpl(
    private val storagePaths: AppStoragePaths,
    private val fileDao: FileDao,
    private val folderDao: FolderDao,
    private val backupDao: BackupDao,
    private val thumbnailDao: ThumbnailDao,
    private val telegramClient: TelegramClient,
    private val secureFileDeleter: SecureFileDeleter,
    private val publishScheduler: PublishScheduler,
    private val pendingDeleteDao: PendingDeleteDao,
    private val filePartDao: FilePartDao,
    private val activeChannel: ActiveChannel,
    private val transferRepository: TransferRepository
) : TrashRepository {

    override fun observeTrash(): Flow<List<TrashItem>> =
        activeChannel.observe().flatMapLatest { chatId ->
            combine(
                fileDao.observeTrashRoots(chatId),
                folderDao.observeTrashRoots(chatId)
            ) { files, folders ->
                (files.map { TrashItem.File(it.toDomain()) } +
                        folders.map { TrashItem.Folder(it.toDomain()) })
                    .sortedByDescending { it.trashedAt }
            }
        }

    override suspend fun trashedChildCounts(folderIds: List<String>): Map<String, Int> {
        if (folderIds.isEmpty()) return emptyMap()
        val counts = mutableMapOf<String, Int>()
        for (row in folderDao.trashedFolderCounts(folderIds)) {
            counts[row.parentId] = (counts[row.parentId] ?: 0) + row.childCount
        }
        for (row in fileDao.trashedFileCounts(folderIds)) {
            counts[row.parentId] = (counts[row.parentId] ?: 0) + row.childCount
        }
        return counts
    }

    override suspend fun trashedChildren(folderId: String): List<TrashItem> {
        val folders = folderDao.trashedChildrenOf(listOf(folderId))
            .map { TrashItem.Folder(it.toDomain()) }
            .sortedBy { it.name.lowercase() }
        val files = fileDao.trashedInFolders(listOf(folderId))
            .map { TrashItem.File(it.toDomain()) }
            .sortedBy { it.name.lowercase() }
        return folders + files
    }

    override suspend fun moveFilesToTrash(ids: List<String>): AppResult<Unit> {
        fileDao.moveToTrash(ids, System.currentTimeMillis())
        markFilesDirty(ids)
        return AppResult.Success(Unit)
    }

    override suspend fun moveFolderToTrash(id: String): AppResult<Unit> {
        val descendants = collectDescendantFolderIds(id)
        val fileIds = descendants.flatMap { fileDao.filesInFolder(it) }.map { it.id }
        val trashedAt = System.currentTimeMillis()
        fileIds.chunked(SQL_BATCH).forEach { fileDao.moveToTrash(it, trashedAt) }
        descendants.reversed().forEach { folderDao.moveToTrash(it, trashedAt) }
        markFolderStateDirty()
        return AppResult.Success(Unit)
    }

    private suspend fun markFilesDirty(ids: List<String>) {
        if (ids.isEmpty()) return
        ids.chunked(SQL_BATCH).forEach { fileDao.markPendingPublish(it) }
        publishScheduler.kick()
    }

    private suspend fun markFolderStateDirty() {
        folderDao.markPendingPublish()
        publishScheduler.kick()
    }

    override suspend fun repairTrashTree() {
        var repaired: Int
        var guard = 0
        do {
            val orphans = folderDao.untrashedChildrenOfTrashed()
            val trashedAt = System.currentTimeMillis()
            orphans.forEach { folder -> folderDao.moveToTrash(folder.id, trashedAt) }
            repaired = orphans.size
        } while (repaired > 0 && guard++ < MAX_FOLDER_DEPTH)
    }

    override suspend fun restoreFiles(ids: List<String>): AppResult<Unit> {
        if (ids.isEmpty()) return AppResult.Success(Unit)
        val entities = ids.chunked(SQL_BATCH).flatMap { fileDao.byIds(it) }
        entities.mapNotNull { it.preTrashFolderId }.distinct().forEach { restoreAncestors(it) }
        val orphaned = entities.filter { entity ->
            val parent = entity.preTrashFolderId
            parent != null && folderDao.byId(parent) == null
        }
        ids.chunked(SQL_BATCH).forEach { fileDao.restoreFromTrash(it) }
        if (orphaned.isNotEmpty()) {
            fileDao.move(orphaned.map { it.id }, null, System.currentTimeMillis())
        }
        markFolderStateDirty()
        markFilesDirty(ids)
        return AppResult.Success(Unit)
    }

    override suspend fun restoreFolder(id: String): AppResult<Unit> {
        val folderIds = collectTrashedDescendantFolderIds(id)
        val fileIds = fileDao.trashedInFolders(folderIds).map { it.id }
        folderIds.reversed().forEach { folderDao.restoreFromTrash(it) }
        restoreAncestors(folderDao.byId(id)?.parentId)
        val restoredParent = folderDao.byId(id)?.parentId
        if (restoredParent != null && folderDao.byId(restoredParent) == null) {
            folderDao.move(id, null, System.currentTimeMillis())
        }
        fileIds.chunked(SQL_BATCH).forEach { fileDao.restoreFromTrash(it) }
        markFolderStateDirty()
        markFilesDirty(fileIds)
        return AppResult.Success(Unit)
    }

    override suspend fun deleteFilesPermanently(ids: List<String>): AppResult<Unit> {
        if (ids.isEmpty()) return AppResult.Success(Unit)
        transferRepository.cancelForFiles(ids)
        val entities = ids.chunked(SQL_BATCH).flatMap { fileDao.byIds(it) }

        val parts = ids.chunked(SQL_BATCH).flatMap { filePartDao.partsOfAll(it) }
        val remoteByChat = (
                entities
                    .filter { it.chatId != null && it.messageId != null }
                    .map { it.chatId!! to it.messageId!! } +
                        parts
                            .filter { it.chatId != null && it.messageId != null }
                            .map { it.chatId!! to it.messageId!! } +
                        entities
                            .filter { it.chatId != null && it.iconFileId != null }
                            .mapNotNull { entity ->
                                val iconMsgId = entity.iconFileId?.substringAfter(":", "")?.toLongOrNull()
                                if (iconMsgId != null) entity.chatId!! to iconMsgId else null
                            }
                )
            .distinct()
            .groupBy({ it.first }, { it.second })

        pendingDeleteDao.upsertAll(
            remoteByChat.flatMap { (chatId, messageIds) ->
                messageIds.map { messageId ->
                    PendingDeleteEntity(chatId, messageId, ids.first())
                }
            }
        )

        for ((chatId, messageIds) in remoteByChat) {
            try {
                telegramClient.deleteMessages(chatId, messageIds)
                SafeLog.d(TAG, "Deleted ${messageIds.size} messages from storage chat")
            } catch (e: TelegramException) {
                SafeLog.w(TAG, "Deleting ${messageIds.size} messages failed", e)
                publishScheduler.kick()
                return AppResult.Failure(
                    if (e.isRateLimit) AppError.RateLimited(e.retryAfterSeconds ?: 0)
                    else AppError.TelegramError(e.code, e.message)
                )
            }
        }
        if (remoteByChat.isEmpty()) {
            SafeLog.d(TAG, "No remote copies to delete for ${entities.size} files")
        }

        for (entity in entities) {
            thumbnailDao.byFileId(entity.id)?.let { thumb ->
                secureFileDeleter.delete(File(thumb.path))
                thumbnailDao.delete(entity.id)
            }
            entity.localPath?.let { path -> deleteStagedCopy(File(path)) }
        }
        ids.chunked(SQL_BATCH).forEach { chunk ->
            backupDao.deleteRecordsForFiles(chunk)
            filePartDao.deleteFor(chunk)
            fileDao.deleteByIds(chunk)
        }
        for ((chatId, messageIds) in remoteByChat) {
            pendingDeleteDao.clear(chatId, messageIds)
        }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteFolderPermanently(id: String): AppResult<Unit> {
        val folderIds = collectTrashedDescendantFolderIds(id)
        val fileIds = fileDao.trashedInFolders(folderIds).map { it.id }
        val result = deleteFilesPermanently(fileIds)
        if (result is AppResult.Failure) return result
        folderIds.reversed().forEach { folderDao.delete(it) }
        markFolderStateDirty()
        return AppResult.Success(Unit)
    }

    /**
     * Import staging copies live in app storage and only exist to feed the
     * uploader, so a permanent delete removes them. Files the user keeps
     * elsewhere on the device are never touched here.
     */
    private fun deleteStagedCopy(file: File) {
        val staging = File(storagePaths.filesDir, IMPORT_DIR)
        if (!file.absolutePath.startsWith(staging.absolutePath + File.separator)) return
        secureFileDeleter.delete(file)
    }

    override suspend fun emptyTrash(): AppResult<Unit> {
        val files = fileDao.trashOlderThan(Long.MAX_VALUE)
        SafeLog.d(TAG, "Emptying trash: ${files.size} files")
        var failure: AppResult.Failure? = null
        for (chunk in files.chunked(REMOTE_DELETE_BATCH)) {
            val result = deleteFilesPermanently(chunk.map { it.id })
            if (result is AppResult.Failure) failure = result
        }
        if (failure != null) return failure
        val folders = folderDao.trashOlderThan(Long.MAX_VALUE)
        folders.forEach { folderDao.delete(it.id) }
        if (folders.isNotEmpty()) markFolderStateDirty()
        return AppResult.Success(Unit)
    }

    override suspend fun clearExpired(days: Int): AppResult<Int> {
        if (days <= 0) return AppResult.Success(0)
        val threshold = System.currentTimeMillis() - days * MILLIS_PER_DAY
        val files = fileDao.trashOlderThan(threshold)
        val result = deleteFilesPermanently(files.map { it.id })
        if (result is AppResult.Failure) return result
        val folders = folderDao.trashOlderThan(threshold)
        folders.forEach { folderDao.delete(it.id) }
        if (folders.isNotEmpty()) markFolderStateDirty()
        return AppResult.Success(files.size + folders.size)
    }

    /**
     * Brings back every trashed folder above the restored item so it lands
     * where it came from. A folder whose parent row is gone for good falls back
     * to the drive root, because there is nothing left to describe it.
     */
    private suspend fun restoreAncestors(folderId: String?) {
        var cursor = folderId
        var guard = 0
        while (cursor != null && guard++ < MAX_FOLDER_DEPTH) {
            val folder = folderDao.byId(cursor) ?: return
            if (folder.trashedAt == null) return
            val parentId = folder.preTrashParentId
            folderDao.restoreFromTrash(cursor)
            if (parentId != null && folderDao.byId(parentId) == null) {
                folderDao.move(cursor, null, System.currentTimeMillis())
                return
            }
            cursor = parentId
        }
    }

    private suspend fun collectTrashedDescendantFolderIds(rootId: String): List<String> {
        val result = mutableListOf(rootId)
        var frontier = listOf(rootId)
        var guard = 0
        while (frontier.isNotEmpty() && guard++ < MAX_FOLDER_DEPTH) {
            frontier = folderDao.trashedChildrenOf(frontier).map { it.id }
            result.addAll(frontier)
        }
        return result
    }

    private suspend fun collectDescendantFolderIds(rootId: String): List<String> {
        val result = mutableListOf(rootId)
        var frontier = listOf(rootId)
        var guard = 0
        while (frontier.isNotEmpty() && guard++ < MAX_FOLDER_DEPTH) {
            frontier = frontier.flatMap { parent ->
                folderDao.childrenOf(parent).map { it.id }
            }
            result.addAll(frontier)
        }
        return result
    }

    companion object {
        private const val TAG = "TrashRepository"
        private const val REMOTE_DELETE_BATCH = 100
        private const val IMPORT_DIR = "imports"
        private const val SQL_BATCH = 500
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        private const val MAX_FOLDER_DEPTH = 64
    }
}
