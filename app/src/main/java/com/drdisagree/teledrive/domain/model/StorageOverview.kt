package com.drdisagree.teledrive.domain.model

data class StorageOverview(
    val totalFiles: Int,
    val remoteBytes: Long,
    val backedUpFiles: Int,
    val pendingBackupFiles: Int,
    val failedBackupFiles: Int,
    val localCacheBytes: Long,
    val deviceFreeBytes: Long
)
