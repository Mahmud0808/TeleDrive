package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult

interface KeyBackupRepository {

    /** Wraps the content key with the passphrase and stores it in the storage channel. */
    suspend fun createBackup(passphrase: CharArray, hint: String?): AppResult<Unit>

    /** Returns true when the passphrase unwrapped the key and it was installed. */
    suspend fun restore(passphrase: CharArray): AppResult<Boolean>

    /** Plaintext passphrase hint from the stored backup, readable without it. */
    suspend fun backupHint(): AppResult<String?>
}
