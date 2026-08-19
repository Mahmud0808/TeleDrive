package com.drdisagree.teledrive.presentation.transfers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferTask
import com.drdisagree.teledrive.domain.model.TransferType
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.common.add
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.EmptyState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.lazy.rememberLazyListState
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift
import androidx.compose.ui.res.stringResource
import com.drdisagree.teledrive.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    onBack: () -> Unit,
    viewModel: TransfersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClearFinished by remember { mutableStateOf(false) }
    var confirmCancelAll by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (confirmClearFinished) {
        ConfirmDialog(
            title = stringResource(R.string.transfers_clear_finished_transfers),
            message = stringResource(R.string.transfers_completed_cancelled_entries_removed),
            confirmLabel = stringResource(R.string.common_clear),
            onConfirm = {
                confirmClearFinished = false
                viewModel.clearFinished()
            },
            onDismiss = { confirmClearFinished = false }
        )
    }

    if (confirmCancelAll) {
        ConfirmDialog(
            title = stringResource(R.string.transfers_cancel_transfers),
            message = stringResource(R.string.transfers_queued_running_paused_failed),
            confirmLabel = stringResource(R.string.transfers_cancel),
            destructive = true,
            onConfirm = {
                confirmCancelAll = false
                viewModel.cancelAll()
            },
            onDismiss = { confirmCancelAll = false }
        )
    }

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)

    Scaffold(
        topBar = {
            TopAppBar(
                    colors = liftedTopAppBarColors(lifted),
                title = { Text(stringResource(R.string.transfers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state.active.isNotEmpty()) {
                        IconButton(onClick = viewModel::pauseAll) {
                            Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.transfers_pause))
                        }
                    } else if (state.paused.isNotEmpty()) {
                        IconButton(onClick = viewModel::resumeAll) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.transfers_resume))
                        }
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_actions))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.transfers_cancel)) },
                            enabled = state.active.isNotEmpty() ||
                                state.paused.isNotEmpty() ||
                                state.failed.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                confirmCancelAll = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.transfers_clear_finished)) },
                            enabled = state.completed.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                confirmClearFinished = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        val isEmpty = state.active.isEmpty() && state.paused.isEmpty() &&
            state.failed.isEmpty() && state.completed.isEmpty()
        if (isEmpty && !state.loading) {
            EmptyState(
                icon = Icons.Outlined.SwapVert,
                title = stringResource(R.string.transfers_no_transfers),
                description = stringResource(R.string.transfers_uploads_downloads_appear_here),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        val activeTitle = stringResource(R.string.transfers_section_active)
        val pausedTitle = stringResource(R.string.transfers_section_paused)
        val finishedTitle = stringResource(R.string.transfers_section_finished)
        val failedTitle = failedSectionTitle(state.failed)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding.add(horizontal = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            section(activeTitle, state.active) { transfer ->
                TransferRow(
                    transfer = transfer,
                    primaryIcon = Icons.Filled.Pause,
                    primaryLabel = stringResource(R.string.common_pause),
                    onPrimary = { viewModel.pause(transfer.id) },
                    onCancel = { viewModel.cancel(transfer.id) }
                )
            }
            section(pausedTitle, state.paused) { transfer ->
                TransferRow(
                    transfer = transfer,
                    primaryIcon = Icons.Filled.PlayArrow,
                    primaryLabel = stringResource(R.string.common_resume),
                    onPrimary = { viewModel.resume(transfer.id) },
                    onCancel = { viewModel.cancel(transfer.id) }
                )
            }
            section(failedTitle, state.failed) { transfer ->
                TransferRow(
                    transfer = transfer,
                    primaryIcon = Icons.Filled.Refresh,
                    primaryLabel = stringResource(R.string.common_retry),
                    onPrimary = { viewModel.retry(transfer.id) },
                    onCancel = { viewModel.cancel(transfer.id) }
                )
            }
            section(finishedTitle, state.completed) { transfer ->
                TransferRow(transfer = transfer)
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    transfers: List<TransferTask>,
    content: @Composable (TransferTask) -> Unit
) {
    if (transfers.isEmpty()) return
    item(key = "header-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
    }
    items(transfers, key = { it.id }) { transfer ->
        content(transfer)
    }
}

/** Names the direction so a failure never reads as a vague "transfer". */
@Composable
private fun failedSectionTitle(failed: List<TransferTask>): String {
    val uploads = failed.count {
        it.type == TransferType.UPLOAD || it.type == TransferType.BACKUP
    }
    return when {
        failed.isEmpty() -> stringResource(R.string.transfers_failed)
        uploads == failed.size -> stringResource(R.string.transfers_failed_uploads)
        uploads == 0 -> stringResource(R.string.transfers_failed_downloads)
        else -> stringResource(R.string.transfers_failed_transfers)
    }
}

@Composable
private fun TransferRow(
    transfer: TransferTask,
    primaryIcon: ImageVector? = null,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    Card(shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TransferThumbnail(transfer = transfer)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(
                                when (transfer.type) {
                                    TransferType.UPLOAD -> stringResource(R.string.transfer_type_upload)
                                    TransferType.DOWNLOAD -> stringResource(R.string.transfer_type_download)
                                    TransferType.BACKUP -> stringResource(R.string.transfer_type_backup)
                                    TransferType.RESTORE -> stringResource(R.string.transfer_type_restore)
                                }
                            )
                            append(" · ")
                            append(Formatters.bytes(transfer.sizeBytes))
                            if (transfer.state == TransferState.RUNNING &&
                                transfer.speedBytesPerSecond > 0
                            ) {
                                append(" · ")
                                append(Formatters.speed(transfer.speedBytesPerSecond))
                                transfer.etaSeconds?.let {
                                    append(" · ")
                                    append(Formatters.eta(it))
                                }
                            }
                            transfer.errorMessage?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (transfer.state == TransferState.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (primaryIcon != null && onPrimary != null) {
                    IconButton(onClick = onPrimary) {
                        Icon(primaryIcon, contentDescription = primaryLabel)
                    }
                }
                if (onCancel != null) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = if (transfer.state.isTerminal) {
                                stringResource(R.string.common_dismiss)
                            } else {
                                stringResource(R.string.common_cancel)
                            }
                        )
                    }
                }
            }
            if (transfer.state == TransferState.RUNNING ||
                transfer.state == TransferState.PAUSED
            ) {
                Spacer(Modifier.height(10.dp))
                LinearWavyProgressIndicator(
                    progress = { transfer.progress },
                    amplitude = { if (transfer.state == TransferState.RUNNING) 1f else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
