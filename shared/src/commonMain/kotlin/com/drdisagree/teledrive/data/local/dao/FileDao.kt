package com.drdisagree.teledrive.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Update
import com.drdisagree.teledrive.data.local.entity.AlbumSummary
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.local.entity.HomeAggregates
import com.drdisagree.teledrive.domain.model.BackupState
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: FileEntity)

    @Update
    suspend fun update(file: FileEntity)

    @Query(
        """SELECT * FROM files
            WHERE sizeBytes = :sizeBytes AND chatId IS :chatId
              AND trashedAt IS NULL AND contentHash IS NOT NULL"""
    )
    suspend fun liveMatchesBySize(sizeBytes: Long, chatId: Long?): List<FileEntity>

    @Query(
        """SELECT category, COUNT(*) AS fileCount, SUM(sizeBytes) AS totalBytes
             FROM files
            WHERE chatId IS :chatId AND trashedAt IS NULL
              AND isHidden = 0 AND isArchived = 0
            GROUP BY category
            ORDER BY totalBytes DESC"""
    )
    fun observeUsageByCategory(chatId: Long?): Flow<List<CategoryUsage>>

    @Query("UPDATE files SET pendingPublish = 1 WHERE id IN (:ids)")
    suspend fun markPendingPublish(ids: List<String>)

    @Query("UPDATE files SET pendingPublish = 1 WHERE folderId IN (:folderIds)")
    suspend fun markPendingPublishInFolders(folderIds: List<String>)

    @Query("UPDATE files SET partCount = :partCount WHERE id = :id")
    suspend fun setPartCount(id: String, partCount: Int)

    @Query("UPDATE files SET pendingPublish = 0 WHERE id = :id")
    suspend fun clearPendingPublish(id: String)

    @Query("SELECT * FROM files WHERE pendingPublish = 1 LIMIT :limit")
    suspend fun pendingPublish(limit: Int): List<FileEntity>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun byId(id: String): FileEntity?

    @Query("SELECT * FROM files WHERE id = :id")
    fun observeById(id: String): Flow<FileEntity?>

    @Query("SELECT * FROM files WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<FileEntity>

    @Query("SELECT * FROM files WHERE id IN (:ids)")
    fun observeByIds(ids: List<String>): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE localPath = :path LIMIT 1")
    suspend fun byLocalPath(path: String): FileEntity?

    @Query(
        """SELECT * FROM files
           WHERE trashedAt IS NOT NULL AND name = :name AND sizeBytes = :sizeBytes"""
    )
    suspend fun trashedMatches(name: String, sizeBytes: Long): List<FileEntity>

    @RawQuery(observedEntities = [FileEntity::class])
    fun pagingSource(query: RoomRawQuery): PagingSource<Int, FileEntity>

    @RawQuery(observedEntities = [FileEntity::class])
    fun observeList(query: RoomRawQuery): Flow<List<FileEntity>>

    @RawQuery
    suspend fun idList(query: RoomRawQuery): List<String>

    @Query(
        """SELECT * FROM files
           WHERE trashedAt IS NULL AND chatId IS :chatId AND isHidden = 0 AND isArchived = 0
           ORDER BY addedAt DESC LIMIT :limit"""
    )
    fun observeRecent(limit: Int, chatId: Long?): Flow<List<FileEntity>>

    @Query(
        """SELECT * FROM files
           WHERE trashedAt IS NULL AND isFavorite = 1 AND chatId IS :chatId
           ORDER BY name COLLATE NOCASE ASC"""
    )
    fun observeFavorites(chatId: Long?): Flow<List<FileEntity>>

    @Query("UPDATE files SET chatId = :chatId WHERE chatId IS NULL")
    suspend fun claimUnownedRows(chatId: Long)

    @Query("SELECT COUNT(*) FROM files WHERE trashedAt IS NULL AND chatId IS :chatId")
    suspend fun fileCount(chatId: Long?): Int

    @Query("DELETE FROM files WHERE chatId = :chatId AND localPath IS NULL")
    suspend fun deleteRemoteOnlyInChat(chatId: Long): Int

    @Query(
        """UPDATE files SET chatId = NULL, messageId = NULL, remoteFileId = NULL,
           remoteUniqueId = NULL, backupState = 'NONE' WHERE chatId = :chatId"""
    )
    suspend fun detachChat(chatId: Long): Int

    @Query(
        """SELECT * FROM files
           WHERE name = :name AND sizeBytes = :sizeBytes
             AND messageId IS NOT NULL AND localPath IS NULL AND trashedAt IS NULL"""
    )
    suspend fun unlinkedRemoteMatches(name: String, sizeBytes: Long): List<FileEntity>

    @Query("SELECT * FROM files WHERE remoteUniqueId IN (:uniqueIds)")
    suspend fun byRemoteUniqueIds(uniqueIds: List<String>): List<FileEntity>

    /**
     * The home screen needs every headline number at once, and separate
     * observed queries each rescan the table on any write. One pass keeps a
     * large library from saturating the query executors during a backup.
     */
    @Query(
        """SELECT COUNT(*) AS total,
                  COALESCE(SUM(CASE WHEN messageId IS NOT NULL THEN sizeBytes END), 0)
                      AS remoteBytes,
                  COALESCE(SUM(CASE WHEN backupState = 'BACKED_UP' THEN 1 END), 0)
                      AS backedUp,
                  COALESCE(SUM(CASE WHEN backupState = 'QUEUED' THEN 1 END), 0) AS queued,
                  COALESCE(SUM(CASE WHEN backupState = 'FAILED' THEN 1 END), 0) AS failed,
                  COALESCE(SUM(
                      CASE WHEN localPath IS NOT NULL AND messageId IS NULL
                                AND backupState IN ('NONE', 'FAILED') THEN 1 END
                  ), 0) AS localOnly
           FROM files
           WHERE trashedAt IS NULL AND chatId IS :chatId"""
    )
    fun observeHomeAggregates(chatId: Long?): Flow<HomeAggregates>

    @Query("UPDATE files SET name = :name, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, modifiedAt: Long)

    @Query("UPDATE files SET folderId = :folderId, modifiedAt = :modifiedAt WHERE id IN (:ids)")
    suspend fun move(ids: List<String>, folderId: String?, modifiedAt: Long)

    @Query("UPDATE files SET isFavorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<String>, favorite: Boolean)

    @Query("UPDATE files SET isHidden = :hidden WHERE id IN (:ids)")
    suspend fun setHidden(ids: List<String>, hidden: Boolean)

    @Query("UPDATE files SET isArchived = :archived WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<String>, archived: Boolean)

    @Query("UPDATE files SET backupState = :state WHERE id = :id")
    suspend fun setBackupState(id: String, state: BackupState)

    @Query("UPDATE files SET backupState = :state WHERE id IN (:ids)")
    suspend fun setBackupStates(ids: List<String>, state: BackupState)

    /**
     * Transfer bookkeeping must never claim a file is unsaved while its copy
     * still sits in the channel, so downgrades only apply to local-only rows.
     */
    @Query("UPDATE files SET backupState = :state WHERE id = :id AND messageId IS NULL")
    suspend fun setBackupStateIfLocalOnly(id: String, state: BackupState)

    @Query(
        """UPDATE files SET backupState = 'BACKED_UP'
           WHERE messageId IS NOT NULL
             AND backupState NOT IN ('BACKED_UP', 'UPLOADING')"""
    )
    suspend fun repairBackedUpStates(): Int

    @Query(
        """UPDATE files SET backupState = 'NONE'
           WHERE backupState IN ('QUEUED', 'UPLOADING')
             AND id NOT IN (
                 SELECT fileId FROM transfers
                  WHERE fileId IS NOT NULL
                    AND state IN ('QUEUED', 'RUNNING', 'PAUSED')
             )"""
    )
    suspend fun clearStaleQueuedStates(): Int

    @Query(
        """UPDATE files SET backupState = 'NONE'
           WHERE backupState = 'FAILED'
             AND id NOT IN (
                 SELECT fileId FROM transfers
                  WHERE fileId IS NOT NULL AND state = 'FAILED'
             )"""
    )
    suspend fun clearStaleFailedStates(): Int

    @Query(
        """DELETE FROM files
           WHERE messageId IS NULL AND trashedAt IS NULL
             AND id IN (
                 SELECT fileId FROM transfers
                  WHERE fileId IS NOT NULL
                    AND backupSessionId IS NOT NULL
                    AND state = 'CANCELLED'
             )"""
    )
    suspend fun deleteCancelledBackupEntries(): Int

    @Query(
        """UPDATE files SET chatId = :chatId, messageId = :messageId,
           remoteFileId = :remoteFileId, remoteUniqueId = :remoteUniqueId,
           backupState = :state WHERE id = :id"""
    )
    suspend fun setRemoteMapping(
        id: String,
        chatId: Long?,
        messageId: Long?,
        remoteFileId: String?,
        remoteUniqueId: String?,
        state: BackupState
    )

    @Query(
        """UPDATE files
           SET messageId = NULL, remoteFileId = NULL, remoteUniqueId = NULL,
               backupState = 'NONE'
           WHERE id = :id"""
    )
    suspend fun detachRemote(id: String)

    @Query("UPDATE files SET localPath = :localPath, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun setLocalPath(id: String, localPath: String?, modifiedAt: Long)

    @Query(
        """UPDATE files SET trashedAt = :trashedAt, preTrashFolderId = folderId, folderId = NULL
           WHERE id IN (:ids)"""
    )
    suspend fun moveToTrash(ids: List<String>, trashedAt: Long)

    @Query(
        """UPDATE files SET folderId = preTrashFolderId, trashedAt = NULL, preTrashFolderId = NULL
           WHERE id IN (:ids)"""
    )
    suspend fun restoreFromTrash(ids: List<String>)

    @Query(
        """SELECT * FROM files
           WHERE trashedAt IS NOT NULL AND chatId IS :chatId
             AND (preTrashFolderId IS NULL
                  OR preTrashFolderId NOT IN
                     (SELECT id FROM folders WHERE trashedAt IS NOT NULL))
           ORDER BY trashedAt DESC"""
    )
    fun observeTrashRoots(chatId: Long?): Flow<List<FileEntity>>

    @Query(
        """SELECT * FROM files
           WHERE trashedAt IS NOT NULL AND preTrashFolderId IN (:folderIds)"""
    )
    suspend fun trashedInFolders(folderIds: List<String>): List<FileEntity>

    @Query(
        """SELECT preTrashFolderId AS parentId, COUNT(*) AS childCount FROM files
           WHERE trashedAt IS NOT NULL AND preTrashFolderId IN (:folderIds)
           GROUP BY preTrashFolderId"""
    )
    suspend fun trashedFileCounts(folderIds: List<String>): List<TrashChildCount>

    @Query("SELECT * FROM files WHERE trashedAt IS NOT NULL AND trashedAt < :threshold")
    suspend fun trashOlderThan(threshold: Long): List<FileEntity>

    @Query("DELETE FROM files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM files WHERE folderId = :folderId AND trashedAt IS NULL")
    suspend fun filesInFolder(folderId: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE messageId IS NOT NULL")
    suspend fun filesWithRemote(): List<FileEntity>

    @Query(
        """SELECT COALESCE(SUM(sizeBytes), 0) FROM files
           WHERE localPath IS NOT NULL
             AND messageId IS NOT NULL
             AND backupState = 'BACKED_UP'"""
    )
    fun observeReclaimableBytes(): Flow<Long>

    @Query(
        """SELECT id FROM files
           WHERE localPath IS NOT NULL
             AND messageId IS NOT NULL
             AND backupState = 'BACKED_UP'"""
    )
    suspend fun reclaimableFileIds(): List<String>

    /**
     * Held only on this device: never uploaded, or canceled or failed on the
     * way up. A backup scan walks device folders and never sees these, so they
     * need queueing by id.
     */
    @Query(
        """SELECT id FROM files
           WHERE trashedAt IS NULL
             AND localPath IS NOT NULL
             AND messageId IS NULL
             AND backupState IN ('NONE', 'FAILED')
             AND chatId IS :chatId"""
    )
    suspend fun localOnlyFileIds(chatId: Long?): List<String>

    @Query(
        """SELECT id FROM files
           WHERE trashedAt IS NULL
             AND messageId IS NULL
             AND backupState = 'QUEUED'
             AND chatId IS :chatId
             AND id NOT IN (
               SELECT fileId FROM transfers
               WHERE state IN ('QUEUED', 'RUNNING', 'PAUSED')
             )"""
    )
    suspend fun queuedWithoutTransferFileIds(chatId: Long?): List<String>

    @Query(
        """SELECT f.folderId AS folderId,
                  fo.name AS name,
                  COUNT(*) AS itemCount,
                  MAX(f.modifiedAt) AS latestAt,
                  (SELECT c.id FROM files c
                     WHERE c.folderId IS f.folderId
                       AND c.trashedAt IS NULL
                       AND c.category IN ('IMAGE', 'VIDEO')
                       AND (:showHidden = 1 OR c.isHidden = 0)
                       AND (:showArchived = 1 OR c.isArchived = 0)
                     ORDER BY c.modifiedAt DESC LIMIT 1) AS coverFileId
           FROM files f
           LEFT JOIN folders fo ON fo.id = f.folderId
           WHERE f.trashedAt IS NULL AND f.chatId IS :chatId
             AND f.category IN ('IMAGE', 'VIDEO')
             AND (:showHidden = 1 OR f.isHidden = 0)
             AND (:showArchived = 1 OR f.isArchived = 0)
           GROUP BY f.folderId
           ORDER BY latestAt DESC"""
    )
    fun observeAlbumsScoped(
        showHidden: Boolean,
        showArchived: Boolean,
        chatId: Long?
    ): Flow<List<AlbumSummary>>

    @Query("SELECT name FROM files WHERE folderId IS :folderId AND trashedAt IS NULL")
    suspend fun namesInFolder(folderId: String?): List<String>

    @Query(
        """SELECT name FROM files
           WHERE folderId IS :folderId AND trashedAt IS NULL AND id != :excludeId"""
    )
    suspend fun namesInFolderExcluding(folderId: String?, excludeId: String): List<String>
}
