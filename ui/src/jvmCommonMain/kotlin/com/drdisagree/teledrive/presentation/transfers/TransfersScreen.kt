package com.drdisagree.teledrive.presentation.transfers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_actions
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_cancel
import com.drdisagree.teledrive.resources.common_clear
import com.drdisagree.teledrive.resources.common_dismiss
import com.drdisagree.teledrive.resources.common_pause
import com.drdisagree.teledrive.resources.common_resume
import com.drdisagree.teledrive.resources.common_retry
import com.drdisagree.teledrive.resources.transfer_stage_joining
import com.drdisagree.teledrive.resources.transfer_stage_sealing
import com.drdisagree.teledrive.resources.transfer_type_backup
import com.drdisagree.teledrive.resources.transfer_type_download
import com.drdisagree.teledrive.resources.transfer_type_restore
import com.drdisagree.teledrive.resources.transfer_type_upload
import com.drdisagree.teledrive.resources.transfers
import com.drdisagree.teledrive.resources.transfers_cancel
import com.drdisagree.teledrive.resources.transfers_cancel_transfers
import com.drdisagree.teledrive.resources.transfers_clear_finished
import com.drdisagree.teledrive.resources.transfers_clear_finished_transfers
import com.drdisagree.teledrive.resources.transfers_completed_canceled_entries_removed
import com.drdisagree.teledrive.resources.transfers_failed
import com.drdisagree.teledrive.resources.transfers_failed_downloads
import com.drdisagree.teledrive.resources.transfers_failed_transfers
import com.drdisagree.teledrive.resources.transfers_failed_uploads
import com.drdisagree.teledrive.resources.transfers_more_queued
import com.drdisagree.teledrive.resources.transfers_no_transfers
import com.drdisagree.teledrive.resources.transfers_pause
import com.drdisagree.teledrive.resources.transfers_queued_running_paused_failed
import com.drdisagree.teledrive.resources.transfers_resume
import com.drdisagree.teledrive.resources.transfers_section_active
import com.drdisagree.teledrive.resources.transfers_section_finished
import com.drdisagree.teledrive.resources.transfers_section_paused
import com.drdisagree.teledrive.resources.transfers_uploads_downloads_appear_here
import com.drdisagree.teledrive.domain.model.TransferStage
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferTask
import com.drdisagree.teledrive.domain.model.TransferType
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.common.add
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    onBack: () -> Unit,
    viewModel: TransfersViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClearFinished by remember { mutableStateOf(false) }
    var confirmCancelAll by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (confirmClearFinished) {
        ConfirmDialog(
            title = stringResource(Res.string.transfers_clear_finished_transfers),
            message = stringResource(Res.string.transfers_completed_canceled_entries_removed),
            confirmLabel = stringResource(Res.string.common_clear),
            onConfirm = {
                confirmClearFinished = false
                viewModel.clearFinished()
            },
            onDismiss = { confirmClearFinished = false }
        )
    }

    if (confirmCancelAll) {
        ConfirmDialog(
            title = stringResource(Res.string.transfers_cancel_transfers),
            message = stringResource(Res.string.transfers_queued_running_paused_failed),
            confirmLabel = stringResource(Res.string.transfers_cancel),
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
                title = { Text(stringResource(Res.string.transfers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
                        )
                    }
                },
                actions = {
                    if (state.active.isNotEmpty()) {
                        IconButton(onClick = viewModel::pauseAll) {
                            Icon(
                                Icons.Filled.Pause,
                                contentDescription = stringResource(Res.string.transfers_pause)
                            )
                        }
                    } else if (state.paused.isNotEmpty()) {
                        IconButton(onClick = viewModel::resumeAll) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = stringResource(Res.string.transfers_resume)
                            )
                        }
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(Res.string.common_actions)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.transfers_cancel)) },
                            enabled = state.active.isNotEmpty() ||
                                    state.paused.isNotEmpty() ||
                                    state.failed.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                confirmCancelAll = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.transfers_clear_finished)) },
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
                title = stringResource(Res.string.transfers_no_transfers),
                description = stringResource(Res.string.transfers_uploads_downloads_appear_here),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        val activeTitle = stringResource(Res.string.transfers_section_active)
        val pausedTitle = stringResource(Res.string.transfers_section_paused)
        val finishedTitle = stringResource(Res.string.transfers_section_finished)
        val failedTitle = failedSectionTitle(state.failed)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding.add(horizontal = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            section(activeTitle, state.active, state.activeTotal) { transfer ->
                TransferRow(
                    transfer = transfer,
                    primaryIcon = Icons.Filled.Pause,
                    primaryLabel = stringResource(Res.string.common_pause),
                    onPrimary = { viewModel.pause(transfer.id) },
                    onCancel = { viewModel.cancel(transfer.id) }
                )
            }
            section(pausedTitle, state.paused, state.pausedTotal) { transfer ->
                TransferRow(
                    transfer = transfer,
                    primaryIcon = Icons.Filled.PlayArrow,
                    primaryLabel = stringResource(Res.string.common_resume),
                    onPrimary = { viewModel.resume(transfer.id) },
                    onCancel = { viewModel.cancel(transfer.id) }
                )
            }
            section(failedTitle, state.failed, state.failedTotal) { transfer ->
                TransferRow(
                    transfer = transfer,
                    primaryIcon = Icons.Filled.Refresh,
                    primaryLabel = stringResource(Res.string.common_retry),
                    onPrimary = { viewModel.retry(transfer.id) },
                    onCancel = { viewModel.cancel(transfer.id) }
                )
            }
            section(finishedTitle, state.completed, state.completedTotal) { transfer ->
                TransferRow(transfer = transfer)
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    transfers: List<TransferTask>,
    total: Int,
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
    val hidden = total - transfers.size
    if (hidden > 0) {
        item(key = "more-$title") {
            Text(
                text = pluralStringResource(Res.plurals.transfers_more_queued, hidden, hidden),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp)
            )
        }
    }
}

/** Names the direction so a failure never reads as a vague "transfer". */
@Composable
private fun failedSectionTitle(failed: List<TransferTask>): String {
    val uploads = failed.count {
        it.type == TransferType.UPLOAD || it.type == TransferType.BACKUP
    }
    return when {
        failed.isEmpty() -> stringResource(Res.string.transfers_failed)
        uploads == failed.size -> stringResource(Res.string.transfers_failed_uploads)
        uploads == 0 -> stringResource(Res.string.transfers_failed_downloads)
        else -> stringResource(Res.string.transfers_failed_transfers)
    }
}

@Composable
private fun transferRate(transfer: TransferTask): String {
    val stage = transfer.stage
    if (stage != null) {
        return stringResource(
            when (stage) {
                TransferStage.SEALING -> Res.string.transfer_stage_sealing
                TransferStage.JOINING -> Res.string.transfer_stage_joining
            }
        )
    }
    if (transfer.state != TransferState.RUNNING || transfer.speedBytesPerSecond <= 0) return ""
    val speed = Formatters.speed(transfer.speedBytesPerSecond)
    val eta = transfer.etaSeconds?.let { Formatters.eta(it) } ?: return speed
    return "$speed · $eta"
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
                                    TransferType.UPLOAD -> stringResource(Res.string.transfer_type_upload)
                                    TransferType.DOWNLOAD -> stringResource(Res.string.transfer_type_download)
                                    TransferType.BACKUP -> stringResource(Res.string.transfer_type_backup)
                                    TransferType.RESTORE -> stringResource(Res.string.transfer_type_restore)
                                }
                            )
                            append(" · ")
                            append(Formatters.bytes(transfer.sizeBytes))
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
                                stringResource(Res.string.common_dismiss)
                            } else {
                                stringResource(Res.string.common_cancel)
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
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Formatters.percent(transfer.progress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = transferRate(transfer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
