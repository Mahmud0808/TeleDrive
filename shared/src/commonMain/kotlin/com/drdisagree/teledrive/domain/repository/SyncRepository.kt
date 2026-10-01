package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import kotlinx.coroutines.flow.Flow

interface SyncRepository {

    val syncing: Flow<Boolean>

    val indexedSoFar: Flow<Int>

    /**
     * Safe after a local wipe: rows whose message vanished become local-only, missing documents are
     * inserted.
     */
    suspend fun fullResync(): AppResult<SyncStats>

    suspend fun incrementalSync(): AppResult<SyncStats>

    /** Runs on every start, so a drive that failed to index during setup does not stay empty. */
    suspend fun syncOnStart(): AppResult<SyncStats>

    /**
     * Renames and moves edit existing messages, which [incrementalSync] never reads.
     * Reports no progress, and returns null when skipped.
     */
    suspend fun catchUpWithRemote(): AppResult<SyncStats>?

    /** Runs until canceled, while the app is in the foreground. */
    suspend fun followRemoteChanges()
}
