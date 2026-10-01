package com.drdisagree.teledrive.presentation.home

import com.drdisagree.teledrive.core.permissions.AppPermission
import com.drdisagree.teledrive.core.telegram.TelegramConnectionState
import com.drdisagree.teledrive.domain.model.BackupHold
import com.drdisagree.teledrive.domain.model.BackupSession
import com.drdisagree.teledrive.domain.model.DriveChannel
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.domain.model.StorageSlice

data class HomeUiState(
    val loading: Boolean = true,
    val connection: TelegramConnectionState = TelegramConnectionState.CONNECTING,
    val offline: Boolean = false,
    val totalFiles: Int = 0,
    val remoteBytes: Long = 0,
    val backedUpCount: Int = 0,
    val pendingCount: Int = 0,
    val localOnlyCount: Int = 0,
    val failedCount: Int = 0,
    val recentFiles: List<DriveFile> = emptyList(),
    val favoriteFolders: List<DriveFolder> = emptyList(),
    val activeBackup: BackupSession? = null,
    val missingPermissions: List<AppPermission> = emptyList(),
    val backupFoldersSelected: Boolean = true,
    val showArchivedSection: Boolean = false,
    val showHiddenSection: Boolean = false,
    val showRecentSection: Boolean = true,
    val rebuilding: Boolean = false,
    val appLockEnabled: Boolean = false,
    val activeTransferCount: Int = 0,
    val offlineBytes: Long = 0,
    val backupHold: BackupHold? = null,
    val activeChannel: DriveChannel? = null,
    val storage: List<StorageSlice> = emptyList(),
    val autoBackupEnabled: Boolean = false,
    val lastBackupAt: Long? = null
)
