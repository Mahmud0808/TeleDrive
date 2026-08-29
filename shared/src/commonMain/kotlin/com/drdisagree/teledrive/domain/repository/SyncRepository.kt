package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import kotlinx.coroutines.flow.Flow

interface SyncRepository {

    /** Emits true while a resync is running. */
    val syncing: Flow<Boolean>

    /** Files indexed so far in the running rebuild; 0 when nothing is running. */
    val indexedSoFar: Flow<Int>

    /**
     * Rebuilds local metadata from the storage chat. Walks the full document
     * history, decodes caption manifests, and reconciles the local database.
     * Local-only rows whose remote message vanished are marked local-only;
     * remote documents missing locally are inserted. Safe after a data wipe.
     */
    suspend fun fullResync(): AppResult<SyncStats>

    /** Fetches only messages newer than the last known message id. */
    suspend fun incrementalSync(): AppResult<SyncStats>

    /**
     * Rebuilds from scratch when nothing is indexed locally, otherwise catches
     * up. Used on every start so a drive that failed to index during setup does
     * not stay empty until the user finds the manual rebuild.
     */
    suspend fun syncOnStart(): AppResult<SyncStats>

    data class SyncStats(
        val inserted: Int,
        val updated: Int,
        val detachedFromRemote: Int,
        /** Encrypted files skipped because the key backup is not restored yet. */
        val lockedFiles: Int = 0
    )
}
