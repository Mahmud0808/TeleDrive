package com.drdisagree.teledrive.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_cancel
import com.drdisagree.teledrive.resources.exclusions_change_file
import com.drdisagree.teledrive.resources.exclusions_change_folder
import com.drdisagree.teledrive.resources.exclusions_choose_file
import com.drdisagree.teledrive.resources.exclusions_choose_folder
import com.drdisagree.teledrive.resources.exclusions_dot_names
import com.drdisagree.teledrive.resources.exclusions_size_in_bytes
import com.drdisagree.teledrive.resources.exclusions_type_extension
import com.drdisagree.teledrive.resources.exclusions_type_file
import com.drdisagree.teledrive.resources.exclusions_type_folder
import com.drdisagree.teledrive.resources.exclusions_type_hidden
import com.drdisagree.teledrive.resources.exclusions_type_max_size
import com.drdisagree.teledrive.resources.exclusions_type_mime
import com.drdisagree.teledrive.resources.exclusions_type_pattern
import com.drdisagree.teledrive.resources.settings_add
import com.drdisagree.teledrive.resources.settings_add_exclusion
import com.drdisagree.teledrive.resources.settings_excluded_files_folders_skipped
import com.drdisagree.teledrive.resources.settings_exclusion_remove_message
import com.drdisagree.teledrive.resources.settings_exclusions
import com.drdisagree.teledrive.resources.settings_item_lives_app_cloud
import com.drdisagree.teledrive.resources.settings_no_exclusions
import com.drdisagree.teledrive.resources.settings_remove
import com.drdisagree.teledrive.resources.settings_remove_exclusion
import com.drdisagree.teledrive.resources.settings_skips_every_file_folder
import com.drdisagree.teledrive.core.files.DocumentTreePaths
import com.drdisagree.teledrive.domain.model.Exclusion
import com.drdisagree.teledrive.domain.model.ExclusionType
import com.drdisagree.teledrive.presentation.common.add
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExclusionsScreen(
    onBack: () -> Unit,
    viewModel: ExclusionsViewModel = koinViewModel()
) {
    val exclusions by viewModel.exclusions.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var exclusionToRemove by remember { mutableStateOf<Exclusion?>(null) }

    exclusionToRemove?.let { exclusion ->
        ConfirmDialog(
            title = stringResource(Res.string.settings_remove_exclusion),
            message = stringResource(Res.string.settings_exclusion_remove_message, exclusion.value),
            confirmLabel = stringResource(Res.string.settings_remove),
            destructive = true,
            onConfirm = {
                exclusionToRemove = null
                viewModel.remove(exclusion.id)
            },
            onDismiss = { exclusionToRemove = null }
        )
    }

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = liftedTopAppBarColors(lifted),
                title = { Text(stringResource(Res.string.settings_exclusions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.settings_add_exclusion)
                )
            }
        }
    ) { padding ->
        if (exclusions.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Block,
                title = stringResource(Res.string.settings_no_exclusions),
                description = stringResource(Res.string.settings_excluded_files_folders_skipped),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding.add(top = 8.dp, bottom = 96.dp)
        ) {
            items(exclusions, key = { it.id }) { exclusion ->
                Row(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayValue(exclusion),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = labelFor(exclusion.type),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = exclusion.enabled,
                        onCheckedChange = { viewModel.setEnabled(exclusion.id, it) }
                    )
                    IconButton(onClick = { exclusionToRemove = exclusion }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(Res.string.settings_remove)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExclusionDialog(
            onAdd = { type, value ->
                viewModel.add(type, value)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddExclusionDialog(
    onAdd: (ExclusionType, String) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(ExclusionType.FOLDER_PATH) }
    var value by remember { mutableStateOf("") }
    var pickerError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val path = uri?.let { DocumentTreePaths.treeToFilePath(context, it) }
        pickerError = uri != null && path == null
        path?.let { value = it }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val path = uri?.let { DocumentTreePaths.documentToFilePath(context, it) }
        pickerError = uri != null && path == null
        path?.let { value = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_add_exclusion)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ExclusionType.FOLDER_PATH,
                        ExclusionType.FILE_PATH,
                        ExclusionType.HIDDEN,
                        ExclusionType.EXTENSION,
                        ExclusionType.MIME_TYPE,
                        ExclusionType.PATH_PATTERN,
                        ExclusionType.MAX_SIZE
                    ).forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = {
                                if (type != option) {
                                    value = ""
                                    pickerError = false
                                }
                                type = option
                            },
                            label = { Text(labelFor(option)) }
                        )
                    }
                }
                if (type == ExclusionType.HIDDEN) {
                    Text(
                        text = stringResource(Res.string.settings_skips_every_file_folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else if (type == ExclusionType.FILE_PATH || type == ExclusionType.FOLDER_PATH) {
                    val isFile = type == ExclusionType.FILE_PATH
                    FilledTonalButton(
                        onClick = {
                            pickerError = false
                            if (isFile) {
                                filePicker.launch(arrayOf("*/*"))
                            } else {
                                folderPicker.launch(null)
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Icon(
                            if (isFile) Icons.AutoMirrored.Filled.InsertDriveFile else Icons.Filled.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                value.isNotEmpty() && isFile -> stringResource(Res.string.exclusions_change_file)
                                value.isNotEmpty() -> stringResource(Res.string.exclusions_change_folder)
                                isFile -> stringResource(Res.string.exclusions_choose_file)
                                else -> stringResource(Res.string.exclusions_choose_folder)
                            }
                        )
                    }
                    if (value.isNotEmpty()) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (pickerError) {
                        Text(
                            text = stringResource(Res.string.settings_item_lives_app_cloud),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(hintFor(type)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(type, if (type == ExclusionType.HIDDEN) "." else value.trim())
                },
                shapes = ButtonDefaults.shapes(),
                enabled = type == ExclusionType.HIDDEN || value.isNotBlank()
            ) { Text(stringResource(Res.string.settings_add)) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}

@Composable
private fun displayValue(exclusion: Exclusion): String = when (exclusion.type) {
    ExclusionType.HIDDEN -> stringResource(Res.string.exclusions_dot_names)
    else -> exclusion.value
}

@Composable
private fun labelFor(type: ExclusionType): String = when (type) {
    ExclusionType.FILE_PATH -> stringResource(Res.string.exclusions_type_file)
    ExclusionType.FOLDER_PATH -> stringResource(Res.string.exclusions_type_folder)
    ExclusionType.EXTENSION -> stringResource(Res.string.exclusions_type_extension)
    ExclusionType.MIME_TYPE -> stringResource(Res.string.exclusions_type_mime)
    ExclusionType.PATH_PATTERN -> stringResource(Res.string.exclusions_type_pattern)
    ExclusionType.MAX_SIZE -> stringResource(Res.string.exclusions_type_max_size)
    ExclusionType.HIDDEN -> stringResource(Res.string.exclusions_type_hidden)
}

@Composable
private fun hintFor(type: ExclusionType): String = when (type) {
    ExclusionType.FILE_PATH -> "/storage/emulated/0/…/file.jpg"
    ExclusionType.FOLDER_PATH -> "/storage/emulated/0/WhatsApp"
    ExclusionType.EXTENSION -> "tmp"
    ExclusionType.MIME_TYPE -> "video/"
    ExclusionType.PATH_PATTERN -> "**/Screenshots/**"
    ExclusionType.MAX_SIZE -> stringResource(Res.string.exclusions_size_in_bytes)
    ExclusionType.HIDDEN -> ""
}
