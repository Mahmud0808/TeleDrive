package com.drdisagree.teledrive.core.transfer

interface BackupSessionTracker {

    suspend fun refreshActive()

    suspend fun refresh(sessionId: String?)
}
