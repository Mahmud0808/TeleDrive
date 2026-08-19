package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.TrashItem
import kotlinx.coroutines.flow.Flow

interface TrashRepository {

    fun observeTrash(): Flow<List<TrashItem>>

    /**
     * Marks folders that were left behind inside an already trashed parent, so
     * their contents show under the folder instead of loose in the trash list.
     */
    suspend fun repairTrashTree()

    /** How many trashed items sit inside each of [folderIds]. */
    suspend fun trashedChildCounts(folderIds: List<String>): Map<String, Int>

    /** Trashed folders and files that went to the trash inside [folderId]. */
    suspend fun trashedChildren(folderId: String): List<TrashItem>

    suspend fun moveFilesToTrash(ids: List<String>): AppResult<Unit>

    suspend fun moveFolderToTrash(id: String): AppResult<Unit>

    suspend fun restoreFiles(ids: List<String>): AppResult<Unit>

    suspend fun restoreFolder(id: String): AppResult<Unit>

    /** Deletes local copies and remote messages. Irreversible. */
    suspend fun deleteFilesPermanently(ids: List<String>): AppResult<Unit>

    suspend fun deleteFolderPermanently(id: String): AppResult<Unit>

    suspend fun emptyTrash(): AppResult<Unit>

    /** Removes trash items older than [days]; returns the number deleted. */
    suspend fun clearExpired(days: Int): AppResult<Int>
}
