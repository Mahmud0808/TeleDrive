package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drdisagree.teledrive.data.local.entity.FilePartEntity

@Dao
interface FilePartDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(part: FilePartEntity)

    @Query("SELECT * FROM file_parts WHERE fileId = :fileId ORDER BY partIndex ASC")
    suspend fun partsOf(fileId: String): List<FilePartEntity>

    @Query("SELECT * FROM file_parts WHERE fileId IN (:fileIds)")
    suspend fun partsOfAll(fileIds: List<String>): List<FilePartEntity>

    @Query("SELECT COUNT(*) FROM file_parts WHERE fileId = :fileId")
    suspend fun countOf(fileId: String): Int

    @Query("DELETE FROM file_parts WHERE fileId IN (:fileIds)")
    suspend fun deleteFor(fileIds: List<String>)

    @Query("SELECT * FROM file_parts WHERE remoteUniqueId = :uniqueId LIMIT 1")
    suspend fun byRemoteUniqueId(uniqueId: String): FilePartEntity?
}
