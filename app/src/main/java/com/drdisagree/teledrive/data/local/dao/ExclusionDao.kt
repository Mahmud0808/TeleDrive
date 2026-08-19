package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.drdisagree.teledrive.data.local.entity.ExclusionEntity
import com.drdisagree.teledrive.domain.model.ExclusionType
import kotlinx.coroutines.flow.Flow

@Dao
interface ExclusionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exclusion: ExclusionEntity)

    @Update
    suspend fun update(exclusion: ExclusionEntity)

    @Query("SELECT * FROM exclusions WHERE chatId IS :chatId ORDER BY createdAt DESC")
    fun observeAll(chatId: Long?): Flow<List<ExclusionEntity>>

    @Query("SELECT * FROM exclusions WHERE enabled = 1 AND chatId IS :chatId")
    suspend fun enabled(chatId: Long?): List<ExclusionEntity>

    @Query("UPDATE exclusions SET chatId = :chatId WHERE chatId IS NULL")
    suspend fun claimUnownedRows(chatId: Long)

    @Query("SELECT COUNT(*) FROM exclusions WHERE chatId IS :chatId AND type = :type")
    suspend fun countByType(chatId: Long?, type: ExclusionType): Int

    @Query("SELECT * FROM exclusions WHERE id = :id")
    suspend fun byId(id: String): ExclusionEntity?

    @Query("UPDATE exclusions SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM exclusions WHERE id = :id")
    suspend fun delete(id: String)
}
