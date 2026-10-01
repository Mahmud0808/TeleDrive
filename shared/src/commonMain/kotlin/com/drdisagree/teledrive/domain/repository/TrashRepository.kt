package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.TrashItem
import kotlinx.coroutines.flow.Flow

interface TrashRepository {

    fun observeTrash(): Flow<List<TrashItem>>

    /** So contents left inside an already trashed parent show under it, not loose in the trash. */
    suspend fun repairTrashTree()

    suspend fun trashedChildCounts(folderIds: List<String>): Map<String, Int>

    suspend fun trashedChildren(folderId: String): List<TrashItem>

    suspend fun moveFilesToTrash(ids: List<String>): AppResult<Unit>

    suspend fun moveFolderToTrash(id: String): AppResult<Unit>

    suspend fun restoreFiles(ids: List<String>): AppResult<Unit>

    suspend fun restoreFolder(id: String): AppResult<Unit>

    /** Irreversible: deletes local copies and remote messages. */
    suspend fun deleteFilesPermanently(ids: List<String>): AppResult<Unit>

    suspend fun deleteFolderPermanently(id: String): AppResult<Unit>

    suspend fun emptyTrash(): AppResult<Unit>

    suspend fun clearExpired(days: Int): AppResult<Int>
}
