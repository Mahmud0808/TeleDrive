package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferTask
import kotlinx.coroutines.flow.Flow

interface TransferRepository {

    fun observeAll(): Flow<List<TransferTask>>

    fun observeActive(): Flow<List<TransferTask>>

    /** Queues an upload of [fileId]'s local copy to the storage chat. */
    suspend fun enqueueUpload(fileId: String, priority: Int = 0): AppResult<String>

    /** Queues a download of [fileId]'s remote copy into local storage. */
    suspend fun enqueueDownload(fileId: String, priority: Int = 0): AppResult<String>

    suspend fun pause(id: String)

    suspend fun resume(id: String)

    suspend fun cancel(id: String)

    /** Stops any queued, running or paused transfer belonging to [fileIds]. */
    suspend fun cancelForFiles(fileIds: List<String>)

    suspend fun retry(id: String)

    /** Bulk controls applied in one database write so the list updates at once. */
    suspend fun pauseAll()

    suspend fun resumeAll()

    suspend fun cancelAll()

    suspend fun clearFinished()

    /** Re-queues transfers left RUNNING by a killed process. */
    suspend fun recoverOrphanedTransfers()
}
