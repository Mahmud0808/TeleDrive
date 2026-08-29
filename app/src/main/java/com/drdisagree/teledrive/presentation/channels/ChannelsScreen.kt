package com.drdisagree.teledrive.presentation.channels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.channels_channel_container
import com.drdisagree.teledrive.resources.channels_create_one_teledrive_store
import com.drdisagree.teledrive.resources.channels_creates_private_channel_account
import com.drdisagree.teledrive.resources.channels_delete_confirm_hint
import com.drdisagree.teledrive.resources.channels_delete_message
import com.drdisagree.teledrive.resources.channels_delete_telegram
import com.drdisagree.teledrive.resources.channels_delete_title
import com.drdisagree.teledrive.resources.channels_drive_actions
import com.drdisagree.teledrive.resources.channels_drive_name
import com.drdisagree.teledrive.resources.channels_look_drives
import com.drdisagree.teledrive.resources.channels_new_drive
import com.drdisagree.teledrive.resources.channels_no_drives_yet
import com.drdisagree.teledrive.resources.channels_open_drive
import com.drdisagree.teledrive.resources.channels_rename_drive
import com.drdisagree.teledrive.resources.channels_renames_channel_telegram_well
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_cancel
import com.drdisagree.teledrive.resources.common_create
import com.drdisagree.teledrive.resources.common_delete_forever
import com.drdisagree.teledrive.resources.common_rename
import com.drdisagree.teledrive.resources.common_storage_channels
import com.drdisagree.teledrive.domain.model.DriveChannel
import com.drdisagree.teledrive.presentation.common.CollectSnackbarMessages
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.common.add
import com.drdisagree.teledrive.presentation.components.BlockingProgressDialog
import com.drdisagree.teledrive.presentation.components.ChannelAvatar
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift

/**
 * Drives this account owns. Every channel keeps its own index, folders and
 * backup selection, so switching is instant and nothing is re-downloaded.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChannelsScreen(
    onBack: () -> Unit,
    viewModel: ChannelsViewModel = koinViewModel()
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DriveChannel?>(null) }
    var deleteTarget by remember { mutableStateOf<DriveChannel?>(null) }

    CollectSnackbarMessages(viewModel.messages, snackbarHostState)

    working?.let { BlockingProgressDialog(message = it) }

    if (showCreate) {
        ChannelNameDialog(
            title = stringResource(Res.string.channels_new_drive),
            description = stringResource(Res.string.channels_creates_private_channel_account),
            confirmLabel = stringResource(Res.string.common_create),
            onConfirm = { label ->
                showCreate = false
                viewModel.create(label)
            },
            onDismiss = { showCreate = false }
        )
    }

    renameTarget?.let { channel ->
        ChannelNameDialog(
            title = stringResource(Res.string.channels_rename_drive),
            description = stringResource(Res.string.channels_renames_channel_telegram_well),
            confirmLabel = stringResource(Res.string.common_rename),
            initialValue = channel.label,
            onConfirm = { label ->
                renameTarget = null
                viewModel.rename(channel.chatId, label)
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { channel ->
        DeleteChannelDialog(
            channel = channel,
            onConfirm = {
                deleteTarget = null
                viewModel.deleteRemotely(channel.chatId)
            },
            onDismiss = { deleteTarget = null }
        )
    }

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = liftedTopAppBarColors(lifted),
                title = { Text(stringResource(Res.string.common_storage_channels)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(Res.string.channels_look_drives)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.channels_new_drive)) }
            )
        }
    ) { padding ->
        if (channels.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.CloudQueue,
                title = stringResource(Res.string.channels_no_drives_yet),
                description = stringResource(Res.string.channels_create_one_teledrive_store),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding.add(horizontal = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(channels, key = { it.chatId }) { channel ->
                ChannelRow(
                    channel = channel,
                    onOpen = { viewModel.switchTo(channel.chatId) },
                    onRename = { renameTarget = channel },
                    onDelete = { deleteTarget = channel },
                    canDelete = channels.size > 1,
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(ROW_FADE_MS),
                        fadeOutSpec = tween(ROW_FADE_MS),
                        placementSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = IntOffset.VisibilityThreshold
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: DriveChannel,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val container by animateColorAsState(
        targetValue = if (channel.isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(CONTAINER_FADE_MS),
        label = stringResource(Res.string.channels_channel_container)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(container)
            .clickable(enabled = !channel.isActive, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        ChannelAvatar(channel = channel)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = channel.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channelSubtitle(channel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AnimatedVisibility(
            visible = channel.isActive,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(Res.string.channels_open_drive),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(Res.string.channels_drive_actions)
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.common_rename)) },
                    leadingIcon = {
                        Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null)
                    },
                    onClick = {
                        showMenu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.channels_delete_telegram)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    enabled = canDelete,
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun channelSubtitle(channel: DriveChannel): String = buildString {
    val count = if (channel.isIndexed) channel.fileCount else channel.remoteFileCount
    append(if (count == 1) "1 file" else "$count files")
    if (channel.isIndexed) {
        append(" · ")
        append(Formatters.bytes(channel.storedBytes))
    } else {
        append(" · opens on first use")
    }
    if (channel.backupFolders.isNotEmpty()) {
        append(" · ")
        append(
            if (channel.backupFolders.size == 1) {
                "1 backup folder"
            } else {
                "${channel.backupFolders.size} backup folders"
            }
        )
    }
}

/**
 * Deleting a drive destroys every file in it for good, so the name has to be
 * typed out. A tap alone is too easy to make by accident.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteChannelDialog(
    channel: DriveChannel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(channel.displayName, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.channels_delete_title, channel.displayName)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        Res.string.channels_delete_message,
                        channel.fileCount,
                        Formatters.bytes(channel.storedBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = {
                        Text(
                            stringResource(
                                Res.string.channels_delete_confirm_hint,
                                channel.displayName
                            )
                        )
                    },
                    singleLine = true,
                    isError = typed.isNotEmpty() && !matches,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                enabled = matches,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text(stringResource(Res.string.common_delete_forever)) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelNameDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = ""
) {
    var label by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(Res.string.channels_drive_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(label.trim()) },
                shapes = ButtonDefaults.shapes(),
                enabled = label.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}

private const val ROW_FADE_MS = 180
private const val CONTAINER_FADE_MS = 220
