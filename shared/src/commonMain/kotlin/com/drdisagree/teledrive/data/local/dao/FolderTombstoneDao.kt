package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drdisagree.teledrive.data.local.entity.FolderTombstoneEntity

@Dao
interface FolderTombstoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tombstones: List<FolderTombstoneEntity>)

    @Query("SELECT * FROM folder_tombstones WHERE chatId IS :chatId")
    suspend fun inChat(chatId: Long?): List<FolderTombstoneEntity>

    @Query("SELECT id FROM folder_tombstones WHERE id = :id LIMIT 1")
    suspend fun idIfDeleted(id: String): String?

    @Query("DELETE FROM folder_tombstones WHERE chatId IS :chatId AND id NOT IN (:keep)")
    suspend fun deleteExcept(chatId: Long?, keep: List<String>)

    @Query("DELETE FROM folder_tombstones WHERE deletedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
