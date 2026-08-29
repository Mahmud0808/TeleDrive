package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_cancel
import com.drdisagree.teledrive.resources.common_create
import com.drdisagree.teledrive.resources.common_drive_root
import com.drdisagree.teledrive.resources.common_folder_name
import com.drdisagree.teledrive.resources.common_no_subfolders
import com.drdisagree.teledrive.resources.common_one_level
import com.drdisagree.teledrive.resources.files_new_folder
import com.drdisagree.teledrive.domain.model.DriveFolder

/**
 * Browsable folder chooser. Navigating into a folder does not select it; the
 * confirm button always targets the folder currently being shown.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderPickerDialog(
    title: String,
    confirmLabel: String,
    childrenOf: (String?) -> List<DriveFolder>,
    nameOf: (String) -> String,
    parentOf: (String) -> String?,
    excludedFolderIds: Set<String> = emptySet(),
    onCreateFolder: ((parentId: String?, name: String) -> Unit)? = null,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var currentId by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    val children = childrenOf(currentId).filterNot { it.id in excludedFolderIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentId != null) {
                    IconButton(onClick = { currentId = parentOf(currentId!!) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_one_level)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(title, modifier = Modifier.weight(1f))
                if (onCreateFolder != null) {
                    IconButton(onClick = { naming = true }) {
                        Icon(
                            Icons.Filled.CreateNewFolder,
                            contentDescription = stringResource(Res.string.files_new_folder)
                        )
                    }
                }
            }
        },
        text = {
            Column {
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = currentId?.let(nameOf) ?: stringResource(Res.string.common_drive_root),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (children.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.common_no_subfolders),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(children, key = { it.id }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentId = folder.id }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentId) },
                shapes = ButtonDefaults.shapes()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.common_cancel)) }
        }
    )

    if (naming && onCreateFolder != null) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(Res.string.files_new_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.common_folder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateFolder(currentId, name.trim())
                        naming = false
                    },
                    enabled = name.isNotBlank(),
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(Res.string.common_create)) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { naming = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(Res.string.common_cancel)) }
            }
        )
    }
}