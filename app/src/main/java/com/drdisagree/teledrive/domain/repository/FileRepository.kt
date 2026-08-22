package com.drdisagree.teledrive.domain.repository

import androidx.paging.PagingData
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.files.LocalCleanup
import com.drdisagree.teledrive.data.local.FileQueryBuilder
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.LinkMetadata
import com.drdisagree.teledrive.domain.model.MediaAlbum
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.model.StorageSlice
import kotlinx.coroutines.flow.Flow

interface FileRepository {

    fun pagedFiles(spec: FileQueryBuilder.Spec): Flow<PagingData<DriveFile>>

    fun observeFiles(spec: FileQueryBuilder.Spec): Flow<List<DriveFile>>

    /** Ids of every file inside [folderId] and all of its subfolders. */
    suspend fun fileIdsInTree(folderId: String): List<String>

    /** Ids of every file matching [spec], including pages not loaded yet. */
    suspend fun fileIds(spec: FileQueryBuilder.Spec): List<String>

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

    /** Folders whose name contains [nameQuery], across the whole active drive. */
    fun searchFolders(
        nameQuery: String,
        showHidden: Boolean = false,
        showArchived: Boolean = false
    ): Flow<List<DriveFolder>>

    /** Storage taken by each file category in the active drive, largest first. */
    fun observeStorageByCategory(): Flow<List<StorageSlice>>

    fun observeFolder(id: String): Flow<DriveFolder?>

    suspend fun getFolder(id: String): DriveFolder?

    fun observeFavoriteFolders(): Flow<List<DriveFolder>>

    suspend fun createFolder(parentId: String?, name: String): AppResult<DriveFolder>

    suspend fun renameFile(id: String, newName: String): AppResult<Unit>

    suspend fun renameFolder(id: String, newName: String): AppResult<Unit>

    suspend fun moveFiles(ids: List<String>, targetFolderId: String?): AppResult<Unit>

    /**
     * Duplicates files into [targetFolderId]. Backed-up files are copied
     * server-side without re-uploading; local-only files are copied on disk.
     * Returns how many copies were created.
     */
    suspend fun copyFiles(ids: List<String>, targetFolderId: String?): AppResult<Int>

    suspend fun moveFolder(id: String, targetParentId: String?): AppResult<Unit>

    suspend fun setFilesFavorite(ids: List<String>, favorite: Boolean)

    suspend fun setFilesHidden(ids: List<String>, hidden: Boolean)

    suspend fun setFilesArchived(ids: List<String>, archived: Boolean)

    suspend fun setFolderFavorite(id: String, favorite: Boolean)

    /** Registers a local file into the drive without uploading it. */
    suspend fun importLocalFile(
        localPath: String,
        folderId: String?,
        displayName: String? = null
    ): AppResult<DriveFile>

    /**
     * Brings a trashed file back when [localPath] holds the very same bytes,
     * so re-uploading a deleted file restores it instead of storing a copy.
     */
    /**
     * A file already in this drive with the same bytes, or null. Size narrows
     * the candidates so the hash is only read when one could actually match.
     */
    /** Live view of specific files, so open menus follow row changes. */
    fun observeFilesByIds(ids: List<String>): Flow<List<DriveFile>>

    /**
     * Clears local paths whose file has vanished from storage, so a copy the
     * user deleted outside the app stops looking downloaded.
     */
    suspend fun reconcileLocalCopies(ids: List<String>)

    /**
     * Writes a note as a Markdown file. Editing replaces the stored copy and
     * its message, so the upload path keeps owning encryption and manifests.
     */
    suspend fun saveNote(
        fileId: String?,
        folderId: String?,
        title: String,
        body: String
    ): AppResult<String>

    suspend fun readNote(fileId: String): AppResult<String>

    /** Link metadata for a saved URL, or null when previews are off. */
    suspend fun linkPreview(url: String): LinkMetadata?

    suspend fun findDuplicate(localPath: String): DriveFile?

    suspend fun reviveTrashedCopy(localPath: String, folderId: String?): DriveFile?

    /** Removes only the local copy; remote copy must be verified first. */
    suspend fun deleteLocalCopy(ids: List<String>): AppResult<LocalCleanup>

    /** Local bytes held by files that are confirmed backed up. */
    fun observeReclaimableBytes(): Flow<Long>

    /** Deletes every local copy that has a verified Telegram backup. */
    suspend fun freeUpSpace(): AppResult<LocalCleanup>
}
