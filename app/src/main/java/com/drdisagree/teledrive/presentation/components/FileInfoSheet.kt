package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.presentation.common.Formatters
import java.util.Locale

/** Detailed metadata sheet available from the browser, gallery, and preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileInfoSheet(
    file: DriveFile,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            InfoRow(stringResource(R.string.info_size), Formatters.bytes(file.sizeBytes))
            InfoRow(stringResource(R.string.info_type), file.mimeType)
            file.extension.takeIf { it.isNotEmpty() }?.let {
                InfoRow(stringResource(R.string.info_extension), it.uppercase(Locale.ROOT))
            }
            if (file.width != null && file.height != null) {
                InfoRow(stringResource(R.string.info_resolution), "${file.width} × ${file.height}")
            }
            file.durationMs?.let {
                InfoRow(
                    stringResource(R.string.info_duration),
                    Formatters.duration(it)
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            InfoRow(stringResource(R.string.info_created), Formatters.dateTime(file.createdAt))
            InfoRow(stringResource(R.string.info_modified), Formatters.dateTime(file.modifiedAt))
            InfoRow(stringResource(R.string.info_added), Formatters.dateTime(file.addedAt))

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            InfoRow(
                stringResource(R.string.info_backup),
                when (file.backupState) {
                    BackupState.BACKED_UP -> stringResource(R.string.info_backup_done)
                    BackupState.UPLOADING -> stringResource(R.string.info_backup_uploading)
                    BackupState.QUEUED -> stringResource(R.string.info_backup_queued)
                    BackupState.FAILED -> stringResource(R.string.info_backup_failed)
                    BackupState.NONE -> stringResource(R.string.info_backup_none)
                }
            )
            InfoRow(
                stringResource(R.string.info_local_copy),
                stringResource(if (file.hasLocalCopy) R.string.info_yes else R.string.info_no)
            )
            InfoRow(
                stringResource(R.string.info_encrypted),
                stringResource(if (file.isEncrypted) R.string.info_yes else R.string.info_no)
            )
            file.localPath?.let {
                InfoRow(
                    stringResource(R.string.info_path),
                    it,
                    monospace = true
                )
            }
            file.contentHash?.let {
                InfoRow(
                    stringResource(R.string.info_sha256),
                    it,
                    monospace = true
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 16.dp)
                .weight(0.35f)
        )
        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            modifier = Modifier.weight(0.65f)
        )
    }
}
