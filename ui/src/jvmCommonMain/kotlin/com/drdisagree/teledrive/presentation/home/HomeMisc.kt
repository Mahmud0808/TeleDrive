package com.drdisagree.teledrive.presentation.home

import com.drdisagree.teledrive.core.network.NetworkStatus
import com.drdisagree.teledrive.core.permissions.AppPermission
import com.drdisagree.teledrive.core.telegram.TelegramConnectionState
import com.drdisagree.teledrive.domain.model.BackupHold
import com.drdisagree.teledrive.domain.model.BackupSession
import com.drdisagree.teledrive.domain.model.DriveChannel

internal data class HomeMisc(
    val syncing: Boolean,
    val session: BackupSession?,
    val connection: TelegramConnectionState,
    val network: NetworkStatus,
    val missing: List<AppPermission>,
    val activeDrive: DriveChannel?,
    val autoBackupEnabled: Boolean,
    val appLockEnabled: Boolean,
    val showArchivedSection: Boolean,
    val showHiddenSection: Boolean,
    val showRecentSection: Boolean,
    val backupHold: BackupHold?
)
