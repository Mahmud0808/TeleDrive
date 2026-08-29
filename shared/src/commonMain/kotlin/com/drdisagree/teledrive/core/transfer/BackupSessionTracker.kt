package com.drdisagree.teledrive.core.transfer

/** Keeps the active backup session's counts current as transfers finish. */
interface BackupSessionTracker {

    suspend fun refreshActive()

    suspend fun refresh(sessionId: String?)
}
