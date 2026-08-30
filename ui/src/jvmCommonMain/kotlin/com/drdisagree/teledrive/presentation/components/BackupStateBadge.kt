package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_backup_failed
import com.drdisagree.teledrive.resources.common_backup_not_backed_up
import com.drdisagree.teledrive.resources.common_backup_queued
import com.drdisagree.teledrive.resources.common_backup_uploading
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.DriveFile

/**
 * Backup state for files that still need attention. A file already in Telegram
 * draws nothing, since that is the normal state and a badge on every row would
 * carry no information.
 */
@Composable
fun BackupStateBadge(file: DriveFile, modifier: Modifier = Modifier) {
    if (file.backupState == BackupState.BACKED_UP) return

    val (icon, tint, label) = when (file.backupState) {
        BackupState.UPLOADING -> Triple(
            Icons.Filled.CloudUpload,
            MaterialTheme.colorScheme.tertiary,
            stringResource(Res.string.common_backup_uploading)
        )

        BackupState.QUEUED -> Triple(
            Icons.Filled.CloudQueue,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(Res.string.common_backup_queued)
        )

        BackupState.FAILED -> Triple(
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.error,
            stringResource(Res.string.common_backup_failed)
        )

        else -> Triple(
            Icons.Filled.CloudOff,
            MaterialTheme.colorScheme.outline,
            stringResource(Res.string.common_backup_not_backed_up)
        )
    }
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = modifier.size(18.dp),
        tint = tint
    )
}
