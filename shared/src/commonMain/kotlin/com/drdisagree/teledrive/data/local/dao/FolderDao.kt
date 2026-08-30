package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("UPDATE folders SET pendingPublish = 1")
    suspend fun markPendingPublish()

    @Query("UPDATE folders SET pendingPublish = 0")
    suspend fun clearPendingPublish()

    @Query("SELECT COUNT(*) FROM folders WHERE pendingPublish = 1")
    suspend fun pendingPublishCount(): Int

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun byId(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeById(id: String): Flow<FolderEntity?>

    @Query(
        """SELECT folders.*,
                  (SELECT COUNT(*) FROM files
                    WHERE files.folderId = folders.id AND files.trashedAt IS NULL
                      AND (:showHidden = 1 OR files.isHidden = 0)
                      AND (:showArchived = 1 OR files.isArchived = 0)) AS fileCount,
                  (SELECT COUNT(*) FROM folders AS child
                    WHERE child.parentId = folders.id AND child.trashedAt IS NULL
                      AND (:showHidden = 1 OR child.isHidden = 0)
                      AND (:showArchived = 1 OR child.isArchived = 0)) AS folderCount
             FROM folders
            WHERE parentId IS :parentId AND chatId IS :chatId AND trashedAt IS NULL
              AND (:showHidden = 1 OR isHidden = 0)
              AND (:showArchived = 1 OR isArchived = 0)
            ORDER BY name COLLATE NOCASE ASC"""
    )
    fun observeChildrenWithCountsScoped(
        parentId: String?,
        chatId: Long?,
        showHidden: Boolean,
        showArchived: Boolean
    ): Flow<List<FolderWithCount>>

    @Query(
        """SELECT folders.*,
                  (SELECT COUNT(*) FROM files
                    WHERE files.folderId = folders.id AND files.trashedAt IS NULL
                      AND (:showHidden = 1 OR files.isHidden = 0)
                      AND (:showArchived = 1 OR files.isArchived = 0)) AS fileCount,
                  (SELECT COUNT(*) FROM folders AS child
                    WHERE child.parentId = folders.id AND child.trashedAt IS NULL
                      AND (:showHidden = 1 OR child.isHidden = 0)
                      AND (:showArchived = 1 OR child.isArchived = 0)) AS folderCount
             FROM folders
            WHERE chatId IS :chatId AND trashedAt IS NULL
              AND name LIKE :pattern ESCAPE '\'
              AND (:showHidden = 1 OR isHidden = 0)
              AND (:showArchived = 1 OR isArchived = 0)
            ORDER BY name COLLATE NOCASE ASC
            LIMIT :limit"""
    )
    fun observeMatchingWithCountsScoped(
        pattern: String,
        chatId: Long?,
        showHidden: Boolean,
        showArchived: Boolean,
        limit: Int
    ): Flow<List<FolderWithCount>>

    @Query("UPDATE folders SET chatId = :chatId WHERE chatId IS NULL")
    suspend fun claimUnownedRows(chatId: Long)

    @Query("SELECT * FROM folders WHERE parentId IS :parentId AND trashedAt IS NULL")
    suspend fun childrenOf(parentId: String?): List<FolderEntity>

    @Query(
        """SELECT * FROM folders
           WHERE trashedAt IS NULL AND isFavorite = 1 AND chatId IS :chatId
           ORDER BY name COLLATE NOCASE ASC"""
    )
    fun observeFavorites(chatId: Long?): Flow<List<FolderEntity>>

    @Query("SELECT name FROM folders WHERE parentId IS :parentId AND trashedAt IS NULL")
    suspend fun namesIn(parentId: String?): List<String>

    @Query(
        """SELECT name FROM folders
           WHERE parentId IS :parentId AND trashedAt IS NULL AND id != :excludeId"""
    )
    suspend fun namesInExcluding(parentId: String?, excludeId: String): List<String>

    @Query("UPDATE folders SET name = :name, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, modifiedAt: Long)

    @Query("UPDATE folders SET parentId = :parentId, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun move(id: String, parentId: String?, modifiedAt: Long)

    @Query("UPDATE folders SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE folders SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Query("UPDATE folders SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query(
        """UPDATE folders SET trashedAt = :trashedAt, preTrashParentId = parentId, parentId = NULL
           WHERE id = :id"""
    )
    suspend fun moveToTrash(id: String, trashedAt: Long)

    @Query(
        """UPDATE folders SET parentId = preTrashParentId, trashedAt = NULL, preTrashParentId = NULL
           WHERE id = :id"""
    )
    suspend fun restoreFromTrash(id: String)

    @Query(
        """SELECT * FROM folders
           WHERE trashedAt IS NOT NULL AND chatId IS :chatId
             AND (preTrashParentId IS NULL
                  OR preTrashParentId NOT IN
                     (SELECT id FROM folders WHERE trashedAt IS NOT NULL))
           ORDER BY trashedAt DESC"""
    )
    fun observeTrashRoots(chatId: Long?): Flow<List<FolderEntity>>

    @Query(
        """SELECT * FROM folders
           WHERE trashedAt IS NOT NULL AND preTrashParentId IN (:parentIds)"""
    )
    suspend fun trashedChildrenOf(parentIds: List<String>): List<FolderEntity>

    @Query(
        """SELECT preTrashParentId AS parentId, COUNT(*) AS childCount FROM folders
           WHERE trashedAt IS NOT NULL AND preTrashParentId IN (:parentIds)
           GROUP BY preTrashParentId"""
    )
    suspend fun trashedFolderCounts(parentIds: List<String>): List<TrashChildCount>

    @Query(
        """SELECT child.* FROM folders AS child
           JOIN folders AS parent ON child.parentId = parent.id
           WHERE child.trashedAt IS NULL AND parent.trashedAt IS NOT NULL"""
    )
    suspend fun untrashedChildrenOfTrashed(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE trashedAt IS NOT NULL AND trashedAt < :threshold")
    suspend fun trashOlderThan(threshold: Long): List<FolderEntity>

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM folders WHERE chatId IS :chatId")
    suspend fun allFolders(chatId: Long?): List<FolderEntity>
}
