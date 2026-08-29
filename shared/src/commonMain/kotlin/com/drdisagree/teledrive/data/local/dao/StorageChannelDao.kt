package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drdisagree.teledrive.data.local.entity.StorageChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageChannelDao {

    @Query("SELECT * FROM storage_channels ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<StorageChannelEntity>>

    @Query("SELECT * FROM storage_channels ORDER BY lastOpenedAt DESC")
    suspend fun all(): List<StorageChannelEntity>

    @Query("SELECT * FROM storage_channels WHERE chatId = :chatId")
    suspend fun byId(chatId: Long): StorageChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: StorageChannelEntity)

    @Query("UPDATE storage_channels SET title = :title WHERE chatId = :chatId")
    suspend fun setTitle(chatId: Long, title: String)

    @Query("UPDATE storage_channels SET backupFolders = :folders WHERE chatId = :chatId")
    suspend fun setBackupFolders(chatId: Long, folders: String)

    @Query("UPDATE storage_channels SET remoteFileCount = :count WHERE chatId = :chatId")
    suspend fun setRemoteFileCount(chatId: Long, count: Int)

    @Query("UPDATE storage_channels SET defaultsSeeded = 1 WHERE chatId = :chatId")
    suspend fun markDefaultsSeeded(chatId: Long)

    @Query("UPDATE storage_channels SET photoPath = :path WHERE chatId = :chatId")
    suspend fun setPhotoPath(chatId: Long, path: String?)

    @Query("UPDATE storage_channels SET lastOpenedAt = :openedAt WHERE chatId = :chatId")
    suspend fun touch(chatId: Long, openedAt: Long)

    @Query("DELETE FROM storage_channels WHERE chatId = :chatId")
    suspend fun delete(chatId: Long)

    @Query("DELETE FROM files WHERE chatId = :chatId")
    suspend fun deleteFiles(chatId: Long)

    @Query("DELETE FROM folders WHERE chatId = :chatId")
    suspend fun deleteFolders(chatId: Long)

    @Query("SELECT COUNT(*) FROM files WHERE chatId = :chatId AND trashedAt IS NULL")
    suspend fun fileCount(chatId: Long): Int

    @Query(
        """SELECT COALESCE(SUM(sizeBytes), 0) FROM files
           WHERE chatId = :chatId AND trashedAt IS NULL AND messageId IS NOT NULL"""
    )
    suspend fun storedBytes(chatId: Long): Long
}
