package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.BackupSession
import com.drdisagree.teledrive.domain.model.BackupTrigger
import kotlinx.coroutines.flow.Flow

interface BackupRepository {

    fun observeActiveSession(): Flow<BackupSession?>

    fun observeLastBackupAt(): Flow<Long?>

    /** Returns the session id, or null when there is nothing to back up. */
    suspend fun startBackup(trigger: BackupTrigger): AppResult<String?>

    suspend fun pauseBackup(sessionId: String)

    suspend fun resumeBackup(sessionId: String)

    suspend fun cancelBackup(sessionId: String)

    suspend fun syncActiveSessionWithSelection()

    /** Called at startup so a session whose transfers finished while the app was dead settles. */
    suspend fun refreshActiveSession()
}
