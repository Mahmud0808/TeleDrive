package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_cancel
import com.drdisagree.teledrive.resources.common_create
import com.drdisagree.teledrive.resources.common_folder_name
import com.drdisagree.teledrive.resources.common_no_subfolders
import com.drdisagree.teledrive.resources.common_one_level
import com.drdisagree.teledrive.resources.files_new_folder
import com.drdisagree.teledrive.resources.picker_folder_create_failed
import com.drdisagree.teledrive.resources.picker_folder_unreadable
import com.drdisagree.teledrive.resources.picker_folder_unreadable_hint
import com.drdisagree.teledrive.resources.picker_internal_storage
import com.drdisagree.teledrive.resources.picker_select_this_folder
import com.drdisagree.teledrive.resources.picker_title_select_folder
import com.drdisagree.teledrive.resources.picker_use_saf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.io.File

private data class QuickFolderChip(
    val label: String,
    val path: String,
    val isHome: Boolean = false
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileSystemFolderPickerDialog(
    rootPath: String,
    listSubfolders: suspend (String) -> FolderListResult,
    createSubfolder: (suspend (parentPath: String, name: String) -> Boolean)? = null,
    onUseSaf: (() -> Unit)? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by remember { mutableStateOf(rootPath) }
    var resultState by remember { mutableStateOf<FolderListResult>(FolderListResult.Success(emptyList())) }
    var loading by remember { mutableStateOf(true) }
    var naming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentPath, listSubfolders) {
        loading = true
        resultState = listSubfolders(currentPath)
        loading = false
    }

    val parentPath = remember(currentPath, rootPath) {
        val trimmed = currentPath.trimEnd('/')
        val rootTrimmed = rootPath.trimEnd('/')
        if (trimmed.isEmpty() || trimmed == rootTrimmed) null
        else {
            val idx = trimmed.lastIndexOf('/')
            if (idx <= 0) "/" else trimmed.substring(0, idx)
        }
    }

    val quickChips = remember(rootPath) {
        val base = rootPath.trimEnd('/')
        val candidates = listOf(
            QuickFolderChip(label = "", path = base, isHome = true),
            QuickFolderChip(label = "Download", path = "$base/Download"),
            QuickFolderChip(label = "Documents", path = "$base/Documents"),
            QuickFolderChip(label = "DCIM", path = "$base/DCIM"),
            QuickFolderChip(label = "Pictures", path = "$base/Pictures")
        )
        candidates.filter { it.isHome || File(it.path).exists() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (parentPath != null) {
                    IconButton(onClick = { currentPath = parentPath }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_one_level)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    stringResource(Res.string.picker_title_select_folder),
                    modifier = Modifier.weight(1f)
                )
                if (createSubfolder != null && resultState is FolderListResult.Success) {
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
                if (quickChips.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickChips.forEach { chip ->
                            FilterChip(
                                selected = if (chip.isHome) currentPath == chip.path else currentPath == chip.path,
                                onClick = { currentPath = chip.path },
                                label = {
                                    Text(
                                        if (chip.isHome) stringResource(Res.string.picker_internal_storage)
                                        else chip.label
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (chip.isHome) Icons.Filled.Home else if (chip.label == "Download") Icons.Filled.SystemUpdateAlt else Icons.Filled.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                when (val res = resultState) {
                    is FolderListResult.Unreadable -> {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(Res.string.picker_folder_unreadable),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(Res.string.picker_folder_unreadable_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is FolderListResult.Success -> {
                        val subfolders = res.items
                        if (subfolders.isEmpty() && !loading) {
                            Text(
                                text = stringResource(Res.string.common_no_subfolders),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                                items(subfolders, key = { it.path }) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { currentPath = item.path }
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
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (onUseSaf != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onUseSaf,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(Res.string.picker_use_saf))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentPath) },
                enabled = resultState is FolderListResult.Success,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.picker_select_this_folder)) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.common_cancel)) }
        }
    )

    if (naming && createSubfolder != null) {
        var name by remember { mutableStateOf("") }
        var createError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(Res.string.files_new_folder)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            createError = false
                        },
                        label = { Text(stringResource(Res.string.common_folder_name)) },
                        isError = createError,
                        supportingText = if (createError) {
                            { Text(stringResource(Res.string.picker_folder_create_failed)) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = createSubfolder(currentPath, name.trim())
                            if (ok) {
                                resultState = listSubfolders(currentPath)
                                naming = false
                            } else {
                                createError = true
                            }
                        }
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
