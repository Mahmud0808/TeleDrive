package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drdisagree.teledrive.data.local.entity.PendingDeleteEntity

@Dao
interface PendingDeleteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(deletes: List<PendingDeleteEntity>)

    @Query("SELECT * FROM pending_deletes LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingDeleteEntity>

    @Query("SELECT messageId FROM pending_deletes WHERE chatId = :chatId")
    suspend fun messageIdsIn(chatId: Long): List<Long>

    @Query("DELETE FROM pending_deletes WHERE chatId = :chatId AND messageId IN (:messageIds)")
    suspend fun clear(chatId: Long, messageIds: List<Long>)
}
