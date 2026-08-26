package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.domain.model.TransferState
import kotlinx.coroutines.flow.Flow
import com.drdisagree.teledrive.domain.model.TransferStage

@Dao
interface TransferDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transfer: TransferEntity)

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun byId(id: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE id = :id")
    fun observeById(id: String): Flow<TransferEntity?>

    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query(
        """SELECT id FROM transfers
            WHERE fileId IN (:fileIds) AND state IN ('QUEUED', 'RUNNING', 'PAUSED')"""
    )
    suspend fun unfinishedIdsForFiles(fileIds: List<String>): List<String>

    @Query("SELECT * FROM transfers WHERE state IN (:states) ORDER BY priority DESC, createdAt ASC")
    fun observeByStates(states: List<TransferState>): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE state = 'QUEUED' ORDER BY priority DESC, createdAt ASC LIMIT :limit")
    suspend fun nextQueued(limit: Int): List<TransferEntity>

    @Query("SELECT COUNT(*) FROM transfers WHERE state IN ('QUEUED', 'RUNNING')")
    fun observeActiveCount(): Flow<Int>

    @Query("UPDATE transfers SET stage = :stage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStage(id: String, stage: TransferStage?, updatedAt: Long)

    @Query(
        """UPDATE transfers SET transferredBytes = :transferred, speedBytesPerSecond = :speed,
           updatedAt = :updatedAt WHERE id = :id"""
    )
    suspend fun updateProgress(id: String, transferred: Long, speed: Long, updatedAt: Long)

    @Query("UPDATE transfers SET state = :state, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setState(id: String, state: TransferState, updatedAt: Long)

    @Query(
        """UPDATE transfers SET state = :state, errorMessage = :error, updatedAt = :updatedAt
           WHERE id = :id"""
    )
    suspend fun setFailed(id: String, state: TransferState, error: String?, updatedAt: Long)

    @Query(
        """UPDATE transfers SET state = :completed, transferredBytes = sizeBytes,
           completedAt = :completedAt, updatedAt = :completedAt WHERE id = :id"""
    )
    suspend fun setCompleted(
        id: String,
        completedAt: Long,
        completed: TransferState = TransferState.COMPLETED
    )

    /** Recovers transfers that were RUNNING when the process died. */
    @Query("UPDATE transfers SET state = 'QUEUED', telegramFileId = NULL WHERE state = 'RUNNING'")
    suspend fun requeueRunning()

    @Query("DELETE FROM transfers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM transfers WHERE state IN ('COMPLETED', 'CANCELLED')")
    suspend fun clearFinished()

    @Query(
        """UPDATE transfers SET state = 'PAUSED', updatedAt = :now
           WHERE state IN ('QUEUED', 'RUNNING')"""
    )
    suspend fun pauseAll(now: Long): Int

    @Query(
        """UPDATE transfers SET state = 'QUEUED', updatedAt = :now
           WHERE state = 'PAUSED'"""
    )
    suspend fun resumeAll(now: Long): Int

    @Query(
        """UPDATE transfers SET state = 'CANCELLED', updatedAt = :now
           WHERE state IN ('QUEUED', 'RUNNING', 'PAUSED', 'FAILED')"""
    )
    suspend fun cancelAll(now: Long): Int

    @Query("SELECT * FROM transfers WHERE backupSessionId = :sessionId")
    suspend fun bySession(sessionId: String): List<TransferEntity>
}
