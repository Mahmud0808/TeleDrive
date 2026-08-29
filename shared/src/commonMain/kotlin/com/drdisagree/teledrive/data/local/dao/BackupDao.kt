package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.drdisagree.teledrive.data.local.entity.BackupRecordEntity
import com.drdisagree.teledrive.data.local.entity.BackupSessionEntity
import com.drdisagree.teledrive.domain.model.BackupSessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: BackupSessionEntity)

    @Update
    suspend fun updateSession(session: BackupSessionEntity)

    @Query("SELECT * FROM backup_sessions WHERE id = :id")
    suspend fun sessionById(id: String): BackupSessionEntity?

    @Query("SELECT * FROM backup_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int): Flow<List<BackupSessionEntity>>

    @Query("SELECT * FROM backup_sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<BackupSessionEntity?>

    @Query(
        """SELECT MAX(completedAt) FROM backup_sessions
            WHERE completedAt IS NOT NULL
              AND status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS')"""
    )
    fun observeLastBackupAt(): Flow<Long?>

    @Query("SELECT * FROM backup_sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeSession(): BackupSessionEntity?

    @Query(
        """UPDATE backup_sessions SET status = :status, completedAt = :completedAt WHERE id = :id"""
    )
    suspend fun setSessionStatus(id: String, status: BackupSessionStatus, completedAt: Long?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(record: BackupRecordEntity)

    @Query("SELECT * FROM backup_records WHERE sourcePath = :sourcePath")
    suspend fun recordByPath(sourcePath: String): BackupRecordEntity?

    @Query("DELETE FROM backup_records WHERE fileId IN (:fileIds)")
    suspend fun deleteRecordsForFiles(fileIds: List<String>)

    @Query(
        """DELETE FROM backup_records
           WHERE fileId IS NULL
              OR fileId NOT IN (SELECT id FROM files WHERE messageId IS NOT NULL)"""
    )
    suspend fun deleteOrphanedRecords(): Int
}
