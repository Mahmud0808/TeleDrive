package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult

interface KeyBackupRepository {

    suspend fun createBackup(passphrase: CharArray, hint: String?): AppResult<Unit>

    suspend fun restore(passphrase: CharArray): AppResult<Boolean>

    /** Stored in plaintext, so it can be read without the passphrase. */
    suspend fun backupHint(): AppResult<String?>
}
