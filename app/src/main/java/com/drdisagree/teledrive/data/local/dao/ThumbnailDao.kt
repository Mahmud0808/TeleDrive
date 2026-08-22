package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drdisagree.teledrive.data.local.entity.ThumbnailEntity

@Dao
interface ThumbnailDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(thumbnail: ThumbnailEntity)

    @Query("SELECT * FROM thumbnails WHERE fileId = :fileId")
    suspend fun byFileId(fileId: String): ThumbnailEntity?

    @Query("UPDATE thumbnails SET lastAccessAt = :accessedAt WHERE fileId = :fileId")
    suspend fun touch(fileId: String, accessedAt: Long)

    @Query("SELECT * FROM thumbnails ORDER BY lastAccessAt ASC LIMIT :limit")
    suspend fun leastRecentlyUsed(limit: Int): List<ThumbnailEntity>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM thumbnails")
    suspend fun totalSizeBytes(): Long

    @Query(
        """
        SELECT t.* FROM thumbnails t
        INNER JOIN files f ON f.id = t.fileId
        WHERE f.mimeType LIKE 'text/%'
            OR f.mimeType IN ('application/json', 'application/xml', 'application/yaml')
        """
    )
    suspend fun textFileThumbnails(): List<ThumbnailEntity>

    @Query("DELETE FROM thumbnails WHERE fileId = :fileId")
    suspend fun delete(fileId: String)

    @Query("DELETE FROM thumbnails")
    suspend fun deleteAll()
}
