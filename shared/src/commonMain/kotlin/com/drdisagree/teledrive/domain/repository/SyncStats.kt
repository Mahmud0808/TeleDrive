package com.drdisagree.teledrive.domain.repository

data class SyncStats(
    val inserted: Int,
    val updated: Int,
    val detachedFromRemote: Int,
    /** Encrypted files skipped because the key backup is not restored yet. */
    val lockedFiles: Int = 0
)
