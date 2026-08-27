package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.TransferTask
import com.drdisagree.teledrive.domain.model.TransferSection
import kotlinx.coroutines.flow.Flow

interface TransferRepository {

    /**
     * The newest rows of one section. A backup can queue tens of thousands of
     * transfers, far more than a list can show, so every observer is bounded.
     */
    fun observeSection(section: TransferSection, limit: Int): Flow<List<TransferTask>>

    fun observeSectionCount(section: TransferSection): Flow<Int>

    fun observeActiveCount(): Flow<Int>

    /** The queued or running transfer that is fetching [fileId], if any. */
    fun observeActiveForFile(fileId: String): Flow<TransferTask?>

    /** Queues an upload of [fileId]'s local copy to the storage chat. */
    suspend fun enqueueUpload(fileId: String, priority: Int = 0): AppResult<String>

    /** Queues every file held only on this device. Returns how many were added. */
    suspend fun enqueuePendingUploads(): AppResult<Int>

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
