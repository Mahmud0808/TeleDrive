package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drdisagree.teledrive.data.local.entity.CacheEntryEntity
import com.drdisagree.teledrive.data.local.entity.CacheEntryType

@Dao
interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CacheEntryEntity)

    @Query("SELECT * FROM cache_entries WHERE fileId = :fileId")
    suspend fun byFileId(fileId: String): List<CacheEntryEntity>

    @Query("UPDATE cache_entries SET lastAccessAt = :accessedAt WHERE path = :path")
    suspend fun touch(path: String, accessedAt: Long)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM cache_entries")
    suspend fun totalSizeBytes(): Long

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM cache_entries WHERE type = :type")
    suspend fun sizeByType(type: CacheEntryType): Long

    @Query("SELECT * FROM cache_entries ORDER BY lastAccessAt ASC LIMIT :limit")
    suspend fun leastRecentlyUsed(limit: Int): List<CacheEntryEntity>

    @Query("SELECT * FROM cache_entries WHERE type = :type")
    suspend fun byType(type: CacheEntryType): List<CacheEntryEntity>

    @Query("DELETE FROM cache_entries WHERE path = :path")
    suspend fun delete(path: String)

    @Query("DELETE FROM cache_entries")
    suspend fun deleteAll()
}
