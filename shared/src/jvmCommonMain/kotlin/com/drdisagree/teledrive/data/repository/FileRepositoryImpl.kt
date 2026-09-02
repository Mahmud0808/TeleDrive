package com.drdisagree.teledrive.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.FileNameUtils
import com.drdisagree.teledrive.core.files.Hashing
import com.drdisagree.teledrive.core.files.LocalCleanup
import com.drdisagree.teledrive.core.files.LocalCopyDeleter
import com.drdisagree.teledrive.core.files.Markdown
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.files.NoteStore
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.data.local.FileQueryBuilder
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.FolderDao
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import com.drdisagree.teledrive.data.mapper.toDomain
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.LinkMetadata
import com.drdisagree.teledrive.domain.model.MediaAlbum
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.model.StorageSlice
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.usecase.FolderCycleCheck
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.io.File
import java.net.URI
import java.util.UUID
import com.drdisagree.teledrive.core.publish.PublishScheduler

@OptIn(ExperimentalCoroutinesApi::class)
class FileRepositoryImpl(
    private val fileDao: FileDao,
    private val folderDao: FolderDao,
    private val publishScheduler: PublishScheduler,
    private val localCopyDeleter: LocalCopyDeleter,
    private val telegramClient: TelegramClient,
    private val manifestCodec: ManifestCodec,
    private val folderPathResolver: FolderPathResolver,
    private val activeChannel: ActiveChannel,
    private val noteStore: NoteStore,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val settingsRepository: SettingsRepository
) : FileRepository {

    override fun pagedFiles(spec: FileQuerySpec): Flow<PagingData<DriveFile>> =
        activeChannel.observe().flatMapLatest { chatId ->
            Pager(
                config = PagingConfig(
                    pageSize = 60,
                    prefetchDistance = 120,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = {
                    fileDao.pagingSource(FileQueryBuilder.build(spec.copy(chatId = chatId)))
                }
            ).flow.map { paging -> paging.map { it.toDomain() } }
        }

    override fun observeFiles(spec: FileQuerySpec): Flow<List<DriveFile>> =
        activeChannel.observe().flatMapLatest { chatId ->
            fileDao.observeList(FileQueryBuilder.build(spec.copy(chatId = chatId)))
                .map { list -> list.map { it.toDomain() } }
        }

    override suspend fun fileIdsInTree(folderId: String): List<String> {
        val ids = mutableListOf<String>()
        var frontier = listOf(folderId)
        var guard = 0
        while (frontier.isNotEmpty() && guard++ < MAX_FOLDER_DEPTH) {
            for (id in frontier) {
                ids += fileDao.filesInFolder(id).map { it.id }
            }
            frontier = frontier.flatMap { parent -> folderDao.childrenOf(parent).map { it.id } }
        }
        return ids
    }

    override suspend fun fileIds(spec: FileQuerySpec): List<String> =
        fileDao.idList(FileQueryBuilder.buildIds(spec.copy(chatId = activeChannel.id())))

    override fun observeFile(id: String): Flow<DriveFile?> =
        fileDao.observeById(id).map { it?.toDomain() }

    override suspend fun getFile(id: String): DriveFile? = fileDao.byId(id)?.toDomain()

    override suspend fun getFiles(ids: List<String>): List<DriveFile> =
        fileDao.byIds(ids).map { it.toDomain() }

    override fun observeRecent(limit: Int): Flow<List<DriveFile>> =
        activeChannel.observe().flatMapLatest { chatId ->
            fileDao.observeRecent(limit, chatId).map { list -> list.map { it.toDomain() } }
        }

    override fun observeAlbums(
        showHidden: Boolean,
        showArchived: Boolean
    ): Flow<List<MediaAlbum>> =
        activeChannel.observe().flatMapLatest { chatId ->
            fileDao.observeAlbumsScoped(showHidden, showArchived, chatId).map { list ->
                list.map { it.toDomain() }
            }
        }

    override fun observeFolders(
        parentId: String?,
        showHidden: Boolean,
        showArchived: Boolean,
        sortField: FileSortField,
        sortDirection: SortDirection
    ): Flow<List<DriveFolder>> =
        activeChannel.observe().flatMapLatest { chatId ->
            folderDao.observeChildrenWithCountsScoped(parentId, chatId, showHidden, showArchived)
                .map { list -> list.map { it.toDomain() }.sortedBy(sortField, sortDirection) }
        }

    /**
     * Folders carry no size, type or backup state, so those fields fall back to
     * the name while still honouring the chosen direction.
     */
    private fun List<DriveFolder>.sortedBy(
        field: FileSortField,
        direction: SortDirection
    ): List<DriveFolder> {
        val ordered = when (field) {
            FileSortField.DATE_MODIFIED -> sortedBy { it.modifiedAt }
            FileSortField.DATE_ADDED -> sortedBy { it.createdAt }
            FileSortField.SIZE -> sortedBy { it.fileCount + it.folderCount }
            else -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
        return if (direction == SortDirection.ASCENDING) ordered else ordered.reversed()
    }

    override fun searchFolders(
        nameQuery: String,
        showHidden: Boolean,
        showArchived: Boolean
    ): Flow<List<DriveFolder>> =
        activeChannel.observe().flatMapLatest { chatId ->
            folderDao.observeMatchingWithCountsScoped(
                pattern = "%${escapeLike(nameQuery)}%",
                chatId = chatId,
                showHidden = showHidden,
                showArchived = showArchived,
                limit = FOLDER_SEARCH_LIMIT
            ).map { list -> list.map { it.toDomain() } }
        }

    override fun observeStorageByCategory(): Flow<List<StorageSlice>> =
        activeChannel.observe().flatMapLatest { chatId ->
            fileDao.observeUsageByCategory(chatId).map { rows ->
                rows.map { StorageSlice(it.category, it.fileCount, it.totalBytes) }
            }
        }

    override fun observeFolder(id: String): Flow<DriveFolder?> =
        folderDao.observeById(id).map { it?.toDomain() }

    override suspend fun getFolder(id: String): DriveFolder? = folderDao.byId(id)?.toDomain()

    override fun observeFavoriteFolders(): Flow<List<DriveFolder>> =
        activeChannel.observe().flatMapLatest { chatId ->
            folderDao.observeFavorites(chatId).map { list -> list.map { it.toDomain() } }
        }

    override suspend fun createFolder(parentId: String?, name: String): AppResult<DriveFolder> {
        val sanitized = FileNameUtils.sanitize(name)
        val siblings = folderDao.namesIn(parentId)
        if (siblings.any { it.equals(sanitized, ignoreCase = true) }) {
            return AppResult.Failure(AppError.FolderNameTaken)
        }
        val now = System.currentTimeMillis()
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            chatId = activeChannel.id(),
            parentId = parentId,
            name = sanitized,
            createdAt = now,
            modifiedAt = now
        )
        folderDao.upsert(folder)
        markFolderStateDirty()
        return AppResult.Success(folder.toDomain())
    }

    override suspend fun renameFile(id: String, newName: String): AppResult<Unit> {
        val file = fileDao.byId(id) ?: return AppResult.Failure(AppError.NotFound)
        val sanitized = FileNameUtils.sanitize(newName)
        val siblingNames = fileDao.namesInFolderExcluding(file.folderId, id)
        val unique = FileNameUtils.uniqueName(sanitized) { candidate ->
            siblingNames.any { it.equals(candidate, ignoreCase = true) }
        }
        val now = System.currentTimeMillis()
        fileDao.rename(id, unique, now)

        file.localPath?.let { path ->
            val localFile = File(path)
            if (localFile.exists()) {
                val renamed = File(localFile.parentFile, unique)
                if (localFile.renameTo(renamed)) {
                    fileDao.setLocalPath(id, renamed.absolutePath)
                }
            }
        }
        markFilesDirty(listOf(id))
        return AppResult.Success(Unit)
    }

    override suspend fun renameFolder(id: String, newName: String): AppResult<Unit> {
        val folder = folderDao.byId(id) ?: return AppResult.Failure(AppError.NotFound)
        val sanitized = FileNameUtils.sanitize(newName)
        val siblings = folderDao.namesInExcluding(folder.parentId, id)
        if (siblings.any { it.equals(sanitized, ignoreCase = true) }) {
            return AppResult.Failure(AppError.FolderNameTaken)
        }
        folderDao.rename(id, sanitized, System.currentTimeMillis())
        markFolderStateDirty()
        markFolderContentsDirty(id)
        return AppResult.Success(Unit)
    }

    override suspend fun moveFiles(ids: List<String>, targetFolderId: String?): AppResult<Unit> {
        if (targetFolderId != null && folderDao.byId(targetFolderId) == null) {
            return AppResult.Failure(AppError.NotFound)
        }
        fileDao.move(ids, targetFolderId, System.currentTimeMillis())
        markFilesDirty(ids)
        return AppResult.Success(Unit)
    }

    override suspend fun copyFiles(
        ids: List<String>,
        targetFolderId: String?
    ): AppResult<Int> {
        if (targetFolderId != null && folderDao.byId(targetFolderId) == null) {
            return AppResult.Failure(AppError.NotFound)
        }
        val existingNames = fileDao.namesInFolder(targetFolderId).toMutableList()
        var copied = 0
        var lastError: AppError? = null

        for (source in fileDao.byIds(ids)) {
            val name = FileNameUtils.uniqueName(source.name) { candidate ->
                existingNames.any { it.equals(candidate, ignoreCase = true) }
            }
            val now = System.currentTimeMillis()
            val copy = source.copy(
                id = UUID.randomUUID().toString(),
                folderId = targetFolderId,
                name = name,
                chatId = null,
                messageId = null,
                remoteFileId = null,
                remoteUniqueId = null,
                backupState = BackupState.NONE,
                localPath = duplicateLocalCopy(source.localPath, name),
                trashedAt = null,
                preTrashFolderId = null,
                addedAt = now,
                modifiedAt = now
            )

            val remoteFileId = source.remoteFileId
            val sourceChatId = source.chatId
            if (remoteFileId != null && sourceChatId != null) {
                when (val result = copyRemote(copy, remoteFileId, sourceChatId)) {
                    is AppResult.Success -> {
                        fileDao.upsert(result.value)
                        existingNames.add(name)
                        copied++
                    }

                    is AppResult.Failure -> lastError = result.error
                }
            } else {
                fileDao.upsert(copy)
                existingNames.add(name)
                copied++
            }
        }
        return if (copied == 0 && lastError != null) {
            AppResult.Failure(lastError)
        } else {
            AppResult.Success(copied)
        }
    }

    private suspend fun copyRemote(
        copy: FileEntity,
        remoteFileId: String,
        chatId: Long
    ): AppResult<FileEntity> = try {
        val manifest = RemoteFileManifest(
            fileId = copy.id,
            name = copy.name,
            folderPath = folderPathResolver.pathOf(copy.folderId),
            folderId = copy.folderId,
            mimeType = copy.mimeType,
            sizeBytes = copy.sizeBytes,
            contentHash = copy.contentHash,
            hidden = copy.isHidden,
            archived = copy.isArchived,
            favorite = copy.isFavorite,
            encrypted = copy.isEncrypted,
            createdAt = copy.createdAt,
            modifiedAt = copy.modifiedAt,
            width = copy.width,
            height = copy.height,
            durationMs = copy.durationMs
        )
        val document = telegramClient.copyDocument(
            chatId = chatId,
            remoteFileId = remoteFileId,
            fileName = if (copy.isEncrypted) "${copy.id}.tde" else copy.name,
            mimeType = if (copy.isEncrypted) "application/octet-stream" else copy.mimeType,
            caption = manifestCodec.encode(manifest, copy.isEncrypted)
        )
        AppResult.Success(
            copy.copy(
                chatId = document.chatId,
                messageId = document.messageId,
                remoteFileId = document.remoteFileId,
                remoteUniqueId = document.uniqueFileId,
                backupState = BackupState.BACKED_UP
            )
        )
    } catch (e: TelegramException) {
        AppResult.Failure(
            if (e.isRateLimit) AppError.RateLimited(e.retryAfterSeconds ?: 0)
            else AppError.TelegramError(e.code, e.message)
        )
    }

    private fun duplicateLocalCopy(sourcePath: String?, name: String): String? {
        val source = sourcePath?.let(::File)?.takeIf { it.exists() } ?: return null
        val target = File(source.parentFile, name)
        return runCatching { source.copyTo(target, overwrite = false).absolutePath }
            .getOrNull()
    }

    override suspend fun moveFolder(id: String, targetParentId: String?): AppResult<Unit> {
        if (FolderCycleCheck.createsCycle(id, targetParentId, ancestorsOf(targetParentId))) {
            return AppResult.Failure(
                AppError.FolderInsideItself
            )
        }
        folderDao.move(id, targetParentId, System.currentTimeMillis())
        markFolderStateDirty()
        markFolderContentsDirty(id)
        return AppResult.Success(Unit)
    }

    override fun observeReclaimableBytes(): Flow<Long> = fileDao.observeReclaimableBytes()

    override suspend fun freeUpSpace(): AppResult<LocalCleanup> =
        deleteLocalCopy(fileDao.reclaimableFileIds())

    private suspend fun ancestorsOf(folderId: String?): List<String> {
        val chain = mutableListOf<String>()
        var cursor = folderId
        var guard = 0
        while (cursor != null && guard++ < MAX_FOLDER_DEPTH) {
            chain += cursor
            cursor = folderDao.byId(cursor)?.parentId
        }
        return chain
    }

    /**
     * Queues a caption rewrite for every file under [folderId], subfolders
     * included, without walking the files themselves: a folder rename can
     * cover thousands of captions and none of them block the caller.
     */
    private suspend fun markFolderContentsDirty(folderId: String) {
        var frontier = listOf(folderId)
        var guard = 0
        while (frontier.isNotEmpty() && guard++ < MAX_FOLDER_DEPTH) {
            fileDao.markPendingPublishInFolders(frontier)
            frontier = frontier.flatMap { parent ->
                folderDao.childrenOf(parent).map { it.id }
            }
        }
        publishScheduler.kick()
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

    override suspend fun setFilesFavorite(ids: List<String>, favorite: Boolean) {
        fileDao.setFavorite(ids, favorite)
        markFilesDirty(ids)
    }

    override suspend fun setFilesHidden(ids: List<String>, hidden: Boolean) {
        fileDao.setHidden(ids, hidden)
        markFilesDirty(ids)
    }

    override suspend fun setFilesArchived(ids: List<String>, archived: Boolean) {
        fileDao.setArchived(ids, archived)
        markFilesDirty(ids)
    }

    override suspend fun setFolderFavorite(id: String, favorite: Boolean) {
        folderDao.setFavorite(id, favorite)
        markFolderStateDirty()
    }

    override suspend fun importLocalFile(
        localPath: String,
        folderId: String?,
        displayName: String?
    ): AppResult<DriveFile> {
        val source = File(localPath)
        if (!source.exists() || !source.isFile) return AppResult.Failure(AppError.NotFound)
        fileDao.byLocalPath(localPath)?.takeIf { it.trashedAt == null }?.let {
            return AppResult.Success(it.toDomain())
        }
        reviveTrashedCopy(localPath, folderId)?.let { return AppResult.Success(it) }

        val name = displayName ?: source.name
        val mime = MimeTypes.fromFileName(name)
        val now = System.currentTimeMillis()
        val entity = FileEntity(
            id = UUID.randomUUID().toString(),
            chatId = activeChannel.id(),
            folderId = folderId,
            name = name,
            sizeBytes = source.length(),
            mimeType = mime,
            category = FileCategory.fromMimeType(mime),
            localPath = source.absolutePath,
            contentHash = null,
            messageId = null,
            remoteFileId = null,
            remoteUniqueId = null,
            backupState = BackupState.NONE,
            createdAt = source.lastModified().takeIf { it > 0 } ?: now,
            modifiedAt = source.lastModified().takeIf { it > 0 } ?: now,
            addedAt = now
        )
        fileDao.upsert(entity)
        return AppResult.Success(entity.toDomain())
    }

    override fun observeFilesByIds(ids: List<String>): Flow<List<DriveFile>> =
        fileDao.observeByIds(ids).map { rows -> rows.map { it.toDomain() } }

    override suspend fun reconcileLocalCopies(ids: List<String>) {
        fileDao.byIds(ids)
            .filter { entity -> entity.localPath?.let { !File(it).exists() } == true }
            .forEach { entity -> fileDao.setLocalPath(entity.id, null) }
    }

    override suspend fun saveNote(
        fileId: String?,
        folderId: String?,
        title: String,
        body: String
    ): AppResult<String> {
        val name = noteStore.fileName(title.ifBlank { fallbackTitle(body) })
        val existing = fileId?.let { fileDao.byId(it) }
        val file = noteStore.write(name, body, existing?.localPath)
        val now = System.currentTimeMillis()

        if (existing == null) {
            return when (val imported = importLocalFile(file.absolutePath, folderId, name)) {
                is AppResult.Success -> AppResult.Success(imported.value.id)
                is AppResult.Failure -> AppResult.Failure(imported.error)
            }
        }

        val chatId = existing.chatId
        val messageId = existing.messageId
        if (chatId != null && messageId != null) {
            runCatching { telegramClient.deleteMessages(chatId, listOf(messageId)) }
        }
        fileDao.upsert(
            existing.copy(
                name = name,
                localPath = file.absolutePath,
                sizeBytes = file.length(),
                contentHash = null,
                messageId = null,
                remoteFileId = null,
                remoteUniqueId = null,
                backupState = BackupState.NONE,
                modifiedAt = now
            )
        )
        return AppResult.Success(existing.id)
    }

    override suspend fun linkPreview(url: String): LinkMetadata? {
        if (!settingsRepository.preferences.first().linkPreviews) return null
        return telegramClient.linkPreview(url, withImage = true)
    }

    override suspend fun readNote(fileId: String): AppResult<String> {
        val entity = fileDao.byId(fileId) ?: return AppResult.Failure(AppError.NotFound)
        entity.localPath?.let(noteStore::read)?.let { return AppResult.Success(it) }
        // Saving replaces the file, but the editor still has to show what is there.
        val fetched = fetchNoteBody(entity) ?: return AppResult.Failure(AppError.NoRemoteCopy)
        return AppResult.Success(fetched)
    }

    private suspend fun fetchNoteBody(entity: FileEntity): String? {
        val remoteFileId = entity.remoteFileId ?: return null
        var downloaded: String? = null
        telegramClient.downloadDocument(remoteFileId).collect { event ->
            if (event is TelegramDownloadEvent.Completed) downloaded = event.localPath
        }
        val source = downloaded?.let(::File)?.takeIf { it.exists() } ?: return null

        val target = File(source.parentFile, "note-${entity.id}.tmp")
        val body = runCatching {
            if (entity.isEncrypted) {
                val key = wrappedKeyRepository.get(CryptoKeys.CONTENT) ?: return null
                source.inputStream().buffered().use { input ->
                    target.outputStream().buffered().use { output ->
                        streamCrypto.decryptStream(key, input, output)
                    }
                }
                target.readText()
            } else {
                source.readText()
            }
        }.getOrNull()
        target.delete()
        return body
    }

    /** A note with no title borrows the link's host, or its first line. */
    private fun fallbackTitle(body: String): String {
        val trimmed = body.trim()
        val firstLine = trimmed.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
        val single = trimmed.lineSequence().count { it.isNotBlank() } == 1
        if (single && firstLine.startsWith("http", ignoreCase = true)) {
            runCatching { URI(firstLine).host }.getOrNull()
                ?.removePrefix("www.")
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return Markdown.plain(firstLine).take(NOTE_TITLE_LIMIT)
    }

    override suspend fun findDuplicate(localPath: String): DriveFile? {
        val source = File(localPath)
        if (!source.exists() || !source.isFile) return null

        /* Re-importing the very same path is the commonest duplicate of all,
           and it needs no hashing to recognize. */
        fileDao.byLocalPath(source.absolutePath)
            ?.takeIf { it.trashedAt == null }
            ?.let { return it.toDomain() }

        val candidates = fileDao.liveMatchesBySize(source.length(), activeChannel.id())
        if (candidates.isEmpty()) return null

        val localHash = Hashing.sha256(source) ?: return null
        return candidates.firstOrNull { it.contentHash == localHash }?.toDomain()
    }

    override suspend fun reviveTrashedCopy(localPath: String, folderId: String?): DriveFile? {
        val source = File(localPath)
        if (!source.exists() || !source.isFile) return null

        val candidates = fileDao.trashedMatches(source.name, source.length())
        if (candidates.isEmpty()) return null

        val localHash = Hashing.sha256(source) ?: return null
        val match = candidates.firstOrNull { it.contentHash == localHash } ?: return null

        val now = System.currentTimeMillis()
        fileDao.restoreFromTrash(listOf(match.id))
        fileDao.move(listOf(match.id), folderId, now)
        fileDao.setLocalPath(match.id, source.absolutePath)
        markFilesDirty(listOf(match.id))
        return fileDao.byId(match.id)?.toDomain()
    }

    override suspend fun deleteLocalCopy(ids: List<String>): AppResult<LocalCleanup> {
        val candidates = fileDao.byIds(ids).filter { entity ->
            entity.localPath != null &&
                    entity.messageId != null &&
                    entity.backupState == BackupState.BACKED_UP
        }
        if (candidates.isEmpty()) return AppResult.Success(LocalCleanup(0))

        val cleanup = localCopyDeleter.delete(candidates.mapNotNull { it.localPath })
        if (cleanup.consentRequest != null) return AppResult.Success(cleanup)

        var cleared = 0
        for (entity in candidates) {
            val path = entity.localPath ?: continue
            if (localCopyDeleter.isGone(path)) {
                fileDao.setLocalPath(entity.id, null)
                cleared++
            }
        }
        return AppResult.Success(LocalCleanup(cleared))
    }

    companion object {
        private const val MAX_FOLDER_DEPTH = 64
        private const val SQL_BATCH = 500
        private const val FOLDER_SEARCH_LIMIT = 200
        private const val NOTE_TITLE_LIMIT = 60
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
