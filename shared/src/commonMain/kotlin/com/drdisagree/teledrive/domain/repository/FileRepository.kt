package com.drdisagree.teledrive.domain.repository

import androidx.paging.PagingData
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.files.LocalCleanup
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.LinkMetadata
import com.drdisagree.teledrive.domain.model.MediaAlbum
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.model.StorageSlice
import kotlinx.coroutines.flow.Flow

interface FileRepository {

    fun pagedFiles(spec: FileQuerySpec): Flow<PagingData<DriveFile>>

    fun observeFiles(spec: FileQuerySpec): Flow<List<DriveFile>>

    suspend fun fileIdsInTree(folderId: String): List<String>

    /** Ids of every file matching [spec], including pages not loaded yet. */
    suspend fun fileIds(spec: FileQuerySpec): List<String>

    fun observeFile(id: String): Flow<DriveFile?>

    suspend fun getFile(id: String): DriveFile?

    suspend fun getFiles(ids: List<String>): List<DriveFile>

    fun observeRecent(limit: Int): Flow<List<DriveFile>>

    fun observeAlbums(showHidden: Boolean, showArchived: Boolean): Flow<List<MediaAlbum>>

    fun observeFolders(
        parentId: String?,
        showHidden: Boolean,
        showArchived: Boolean,
        sortField: FileSortField = FileSortField.NAME,
        sortDirection: SortDirection = SortDirection.ASCENDING
    ): Flow<List<DriveFolder>>

    fun searchFolders(
        nameQuery: String,
        showHidden: Boolean = false,
        showArchived: Boolean = false
    ): Flow<List<DriveFolder>>

    fun observeStorageByCategory(): Flow<List<StorageSlice>>

    fun observeFolder(id: String): Flow<DriveFolder?>

    suspend fun getFolder(id: String): DriveFolder?

    fun observeFavoriteFolders(): Flow<List<DriveFolder>>

    fun observeAvailableOfflineFolders(): Flow<List<DriveFolder>>

    /** Files available offline, directly or through a folder, with no copy on this device. */
    fun observeAvailableOfflineMissingIds(): Flow<List<String>>

    fun observeArchivedFolders(): Flow<List<DriveFolder>>

    fun observeHiddenFolders(): Flow<List<DriveFolder>>

    suspend fun createFolder(parentId: String?, name: String): AppResult<DriveFolder>

    suspend fun renameFile(id: String, newName: String): AppResult<Unit>

    suspend fun renameFolder(id: String, newName: String): AppResult<Unit>

    suspend fun moveFiles(ids: List<String>, targetFolderId: String?): AppResult<Unit>

    /**
     * Backed-up files are copied server side without uploading again; returns how many copies were
     * made.
     */
    suspend fun copyFiles(ids: List<String>, targetFolderId: String?): AppResult<Int>

    suspend fun moveFolder(id: String, targetParentId: String?): AppResult<Unit>

    suspend fun setFilesFavorite(ids: List<String>, favorite: Boolean)

    suspend fun setFilesAvailableOffline(ids: List<String>, available: Boolean)

    suspend fun setFilesHidden(ids: List<String>, hidden: Boolean)

    suspend fun setFilesArchived(ids: List<String>, archived: Boolean)

    suspend fun setFolderFavorite(id: String, favorite: Boolean)

    suspend fun setFolderHidden(id: String, hidden: Boolean)

    suspend fun setFolderArchived(id: String, archived: Boolean)

    suspend fun setFolderAvailableOffline(id: String, available: Boolean)

    suspend fun importLocalFile(
        localPath: String,
        folderId: String?,
        displayName: String? = null
    ): AppResult<DriveFile>

    fun observeFilesByIds(ids: List<String>): Flow<List<DriveFile>>

    suspend fun filesByIds(ids: List<String>): List<DriveFile>

    /** So a copy deleted outside the app stops looking downloaded. */
    suspend fun reconcileLocalCopies(ids: List<String>)

    /**
     * Editing replaces the stored copy and its message, so the upload path keeps owning encryption
     * and manifests.
     */
    suspend fun saveNote(
        fileId: String?,
        folderId: String?,
        title: String,
        body: String
    ): AppResult<String>

    suspend fun readNote(fileId: String): AppResult<String>

    suspend fun linkPreview(url: String): LinkMetadata?

    suspend fun findDuplicate(localPath: String): DriveFile?

    suspend fun sweepImportOrphans()

    suspend fun resolveImportFolder(relativePath: String, parentId: String?): String?

    suspend fun reviveTrashedCopy(localPath: String, folderId: String?): DriveFile?

    /** Removes only the local copy; remote copy must be verified first. */
    suspend fun deleteLocalCopy(ids: List<String>): AppResult<LocalCleanup>

    fun observeReclaimableBytes(): Flow<Long>

    suspend fun freeUpSpace(): AppResult<LocalCleanup>
}
