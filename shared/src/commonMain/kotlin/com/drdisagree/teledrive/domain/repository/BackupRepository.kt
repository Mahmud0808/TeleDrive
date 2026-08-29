package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.BackupSession
import com.drdisagree.teledrive.domain.model.BackupTrigger
import kotlinx.coroutines.flow.Flow

interface BackupRepository {

    fun observeActiveSession(): Flow<BackupSession?>

    /** When the last backup finished, or null if none ever has. */
    fun observeLastBackupAt(): Flow<Long?>

    /**
     * Scans backup folders, applies exclusions and incremental rules, then
     * queues uploads. Returns the session id, or null when nothing to back up.
     */
    suspend fun startBackup(trigger: BackupTrigger): AppResult<String?>

    suspend fun pauseBackup(sessionId: String)

    suspend fun resumeBackup(sessionId: String)

    suspend fun cancelBackup(sessionId: String)

    /**
     * Drops queued or running backup transfers whose source no longer sits in a
     * selected backup folder, then recounts the running session.
     */
    suspend fun syncActiveSessionWithSelection()

    /**
     * Recounts the running session from its transfers. Called at startup so a
     * session whose transfers all finished while the app was dead settles
     * instead of showing progress forever.
     */
    suspend fun refreshActiveSession()
}
