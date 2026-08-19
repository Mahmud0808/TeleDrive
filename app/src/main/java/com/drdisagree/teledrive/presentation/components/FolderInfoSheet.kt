package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.presentation.common.Formatters

/** Folder counterpart to [FileInfoSheet], with what a folder actually has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderInfoSheet(folder: DriveFolder, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(text = folder.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            FolderInfoRow(
                label = stringResource(R.string.info_contents),
                value = folder.contentsLabel()
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            FolderInfoRow(
                label = stringResource(R.string.info_created),
                value = Formatters.dateTime(folder.createdAt)
            )
            FolderInfoRow(
                label = stringResource(R.string.info_modified),
                value = Formatters.dateTime(folder.modifiedAt)
            )
            FolderInfoRow(
                label = stringResource(R.string.info_favorite),
                value = stringResource(
                    if (folder.isFavorite) R.string.info_yes else R.string.info_no
                )
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FolderInfoRow(label: String, value: String) {
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
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.65f)
        )
    }
}
