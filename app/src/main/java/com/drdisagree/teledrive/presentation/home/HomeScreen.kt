package com.drdisagree.teledrive.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.app_backed_up
import com.drdisagree.teledrive.resources.app_name
import com.drdisagree.teledrive.resources.backup_age_days
import com.drdisagree.teledrive.resources.backup_age_hours
import com.drdisagree.teledrive.resources.backup_age_minutes
import com.drdisagree.teledrive.resources.common_cancel
import com.drdisagree.teledrive.resources.common_pause
import com.drdisagree.teledrive.resources.common_resume
import com.drdisagree.teledrive.resources.file_count
import com.drdisagree.teledrive.resources.home_active_transfers
import com.drdisagree.teledrive.resources.home_back_up_pending
import com.drdisagree.teledrive.resources.home_backing_up
import com.drdisagree.teledrive.resources.home_backup_all_in_telegram
import com.drdisagree.teledrive.resources.home_backup_at
import com.drdisagree.teledrive.resources.home_backup_auto_off
import com.drdisagree.teledrive.resources.home_backup_just_now
import com.drdisagree.teledrive.resources.home_backup_never
import com.drdisagree.teledrive.resources.home_backup_nothing_to_back_up
import com.drdisagree.teledrive.resources.home_backup_paused
import com.drdisagree.teledrive.resources.home_cancel_backup_action
import com.drdisagree.teledrive.resources.home_cancel_backup_title
import com.drdisagree.teledrive.resources.home_choose_folders
import com.drdisagree.teledrive.resources.home_deleted_files_kept_removal
import com.drdisagree.teledrive.resources.home_failed_count
import com.drdisagree.teledrive.resources.home_lock_app
import com.drdisagree.teledrive.resources.home_not_backed_up_count
import com.drdisagree.teledrive.resources.home_open_drives
import com.drdisagree.teledrive.resources.home_queued_uploads_discarded_files
import com.drdisagree.teledrive.resources.home_scan_now
import com.drdisagree.teledrive.resources.home_scanning
import com.drdisagree.teledrive.resources.home_section_favorites
import com.drdisagree.teledrive.resources.home_section_recent
import com.drdisagree.teledrive.resources.home_section_storage
import com.drdisagree.teledrive.resources.home_session_progress
import com.drdisagree.teledrive.resources.home_status_connected
import com.drdisagree.teledrive.resources.home_status_connecting
import com.drdisagree.teledrive.resources.home_status_offline
import com.drdisagree.teledrive.resources.home_status_rebuilding
import com.drdisagree.teledrive.resources.home_status_syncing
import com.drdisagree.teledrive.resources.home_storage_percent
import com.drdisagree.teledrive.resources.home_storage_percent_min
import com.drdisagree.teledrive.resources.home_transfer_history
import com.drdisagree.teledrive.resources.home_transfer_history_subtitle
import com.drdisagree.teledrive.resources.home_waiting_count
import com.drdisagree.teledrive.resources.trash
import com.drdisagree.teledrive.core.telegram.TelegramConnectionState
import com.drdisagree.teledrive.domain.model.BackupSessionStatus
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.model.StorageSlice
import com.drdisagree.teledrive.presentation.collection.CollectionType
import com.drdisagree.teledrive.presentation.common.AgeBucket
import com.drdisagree.teledrive.presentation.common.CollectSnackbarMessages
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.common.add
import com.drdisagree.teledrive.presentation.components.BottomBarSnackbarHost
import com.drdisagree.teledrive.presentation.components.ChannelAvatar
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.ConnectionDot
import com.drdisagree.teledrive.presentation.components.ConnectionIndicator
import com.drdisagree.teledrive.presentation.components.FileThumbnail
import com.drdisagree.teledrive.presentation.components.GroupedList
import com.drdisagree.teledrive.presentation.components.LoadingState
import com.drdisagree.teledrive.presentation.components.PermissionWarningCard
import com.drdisagree.teledrive.presentation.components.chartColor
import com.drdisagree.teledrive.presentation.components.label
import com.drdisagree.teledrive.presentation.navigation.LocalBottomBarInset
import com.drdisagree.teledrive.presentation.preview.PreviewSequence
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onOpenFile: (String, PreviewSequence) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenCollection: (CollectionType) -> Unit,
    onOpenBackupSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var lastConnectionStatus by remember { mutableStateOf<ConnectionStatus?>(null) }
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }

    var confirmCancelBackup by remember { mutableStateOf<String?>(null) }
    confirmCancelBackup?.let { sessionId ->
        ConfirmDialog(
            title = stringResource(Res.string.home_cancel_backup_title),
            message = stringResource(Res.string.home_queued_uploads_discarded_files),
            confirmLabel = stringResource(Res.string.home_cancel_backup_action),
            destructive = true,
            onConfirm = {
                confirmCancelBackup = null
                viewModel.cancelBackup(sessionId)
            },
            onDismiss = { confirmCancelBackup = null }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    CollectSnackbarMessages(viewModel.messages, snackbarHostState)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { BottomBarSnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                subtitle = {
                    val status = rememberConnectionStatus(
                        offline = state.offline,
                        connection = state.connection,
                        known = !state.loading
                    )
                    AnimatedVisibility(
                        visible = status != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val shown = status ?: lastConnectionStatus
                        if (shown != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ConnectionDot(status = shown.indicator)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(shown.labelRes))
                            }
                        }
                    }
                    lastConnectionStatus = status ?: lastConnectionStatus
                },
                actions = {
                    if (state.appLockEnabled) {
                        IconButton(onClick = viewModel::lockNow) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = stringResource(Res.string.home_lock_app)
                            )
                        }
                    }
                    state.activeChannel?.let { channel ->
                        IconButton(
                            onClick = onOpenChannels,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            ChannelAvatar(
                                channel = channel,
                                size = AVATAR_SIZE,
                                contentDescription = stringResource(
                                    Res.string.home_open_drives
                                )
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding.add(
                horizontal = 16.dp,
                top = 8.dp,
                bottom = 24.dp + LocalBottomBarInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.missingPermissions.isNotEmpty()) {
                item {
                    PermissionWarningCard(
                        missing = state.missingPermissions,
                        onGrant = {
                            permissionLauncher.launch(
                                state.missingPermissions
                                    .mapNotNull { it.manifestPermission }
                                    .distinct()
                                    .toTypedArray()
                            )
                        }
                    )
                }
            }
            item {
                BackupCard(
                    state = state,
                    scanning = scanning,
                    onChooseFolders = onOpenBackupSettings,
                    onScanNow = viewModel::scanNow,
                    onBackUpPending = viewModel::backUpPending,
                    onPause = viewModel::pauseBackup,
                    onResume = viewModel::resumeBackup,
                    onCancel = { confirmCancelBackup = it }
                )
            }
            if (state.favoriteFolders.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(Res.string.home_section_favorites))
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.favoriteFolders.take(5).forEach { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onOpenFolder(folder.id) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    folder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (state.showRecentSection && state.recentFiles.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(Res.string.home_section_recent))
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.recentFiles, key = { it.id }) { file ->
                            Column(
                                modifier = Modifier
                                    .width(112.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable {
                                        onOpenFile(
                                            file.id,
                                            PreviewSequence(
                                                sortField = FileSortField.DATE_ADDED,
                                                sortDirection = SortDirection.DESCENDING
                                            )
                                        )
                                    }
                                    .padding(6.dp)
                            ) {
                                FileThumbnail(
                                    file = file,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (state.storage.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(Res.string.home_section_storage))
                    Spacer(Modifier.height(8.dp))
                    StorageCard(slices = state.storage)
                }
            }
            item {
                CollectionLinks(
                    showArchived = state.showArchivedSection,
                    showHidden = state.showHiddenSection,
                    activeTransferCount = state.activeTransferCount,
                    onOpenCollection = onOpenCollection,
                    onOpenTransfers = onOpenTransfers,
                    onOpenTrash = onOpenTrash
                )
            }
        }
    }
}

@Composable
private fun CollectionLinks(
    showArchived: Boolean,
    showHidden: Boolean,
    activeTransferCount: Int,
    onOpenCollection: (CollectionType) -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenTrash: () -> Unit
) {
    GroupedList(horizontalPadding = 0.dp) {
        CollectionType.entries.forEach { collection ->
            val visible = when (collection) {
                CollectionType.ARCHIVED -> showArchived
                CollectionType.HIDDEN -> showHidden
                else -> true
            }
            add(visible = visible) {
                CollectionRow(
                    icon = collection.icon,
                    title = stringResource(collection.titleRes),
                    subtitle = stringResource(collection.subtitleRes),
                    onClick = { onOpenCollection(collection) }
                )
            }
        }
        add {
            CollectionRow(
                icon = Icons.Filled.SwapVert,
                title = stringResource(Res.string.home_transfer_history),
                subtitle = if (activeTransferCount > 0) {
                    stringResource(Res.string.home_active_transfers, activeTransferCount)
                } else {
                    stringResource(Res.string.home_transfer_history_subtitle)
                },
                onClick = onOpenTransfers
            )
        }
        add {
            CollectionRow(
                icon = Icons.Filled.Delete,
                title = stringResource(Res.string.trash),
                subtitle = stringResource(Res.string.home_deleted_files_kept_removal),
                onClick = onOpenTrash
            )
        }
    }
}

@Composable
private fun CollectionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupCard(
    state: HomeUiState,
    scanning: Boolean,
    onChooseFolders: () -> Unit,
    onScanNow: () -> Unit,
    onBackUpPending: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        state.rebuilding -> Icons.Filled.CloudSync
                        state.failedCount > 0 -> Icons.Outlined.ErrorOutline
                        state.activeBackup?.status == BackupSessionStatus.PAUSED ->
                            Icons.Filled.CloudSync

                        state.pendingCount > 0 -> Icons.Filled.CloudUpload
                        state.offline -> Icons.Filled.CloudOff
                        else -> Icons.Filled.CloudDone
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = when {
                            state.activeBackup?.status == BackupSessionStatus.RUNNING ->
                                stringResource(Res.string.home_backing_up)

                            state.activeBackup?.status == BackupSessionStatus.PAUSED ->
                                stringResource(Res.string.home_backup_paused)

                            state.rebuilding -> stringResource(Res.string.home_status_rebuilding)
                            state.totalFiles == 0 ->
                                stringResource(Res.string.home_backup_nothing_to_back_up)

                            state.failedCount > 0 ->
                                stringResource(Res.string.home_failed_count, state.failedCount)

                            state.pendingCount > 0 ->
                                stringResource(Res.string.home_waiting_count, state.pendingCount)

                            state.backedUpCount < state.totalFiles -> stringResource(
                                Res.string.home_not_backed_up_count,
                                state.totalFiles - state.backedUpCount
                            )

                            else -> stringResource(Res.string.app_backed_up)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = backupFreshnessLabel(
                            autoBackupEnabled = state.autoBackupEnabled,
                            lastBackupAt = state.lastBackupAt,
                            totalFiles = state.totalFiles,
                            backedUpCount = state.backedUpCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            state.activeBackup?.let { session ->
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sessionButtonColors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    if (session.status == BackupSessionStatus.PAUSED) {
                        Button(
                            onClick = { onResume(session.id) },
                            shapes = ButtonDefaults.shapes(),
                            colors = sessionButtonColors
                        ) { Text(stringResource(Res.string.common_resume)) }
                    } else {
                        Button(
                            onClick = { onPause(session.id) },
                            shapes = ButtonDefaults.shapes(),
                            colors = sessionButtonColors
                        ) { Text(stringResource(Res.string.common_pause)) }
                    }
                    OutlinedButton(
                        onClick = { onCancel(session.id) },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                .copy(alpha = 0.5f)
                        )
                    ) { Text(stringResource(Res.string.common_cancel)) }
                }
                Spacer(Modifier.height(12.dp))
                LinearWavyProgressIndicator(
                    progress = { session.progress },
                    amplitude = {
                        if (session.status == BackupSessionStatus.RUNNING) 1f else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        Res.string.home_session_progress,
                        session.completedFiles,
                        session.totalFiles
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (state.rebuilding && state.activeBackup == null) {
                Spacer(Modifier.height(12.dp))
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.activeBackup == null) {
                Spacer(Modifier.height(16.dp))
                val foldersSelected = state.backupFoldersSelected
                val pending = state.localOnlyCount
                Button(
                    onClick = when {
                        pending > 0 -> onBackUpPending
                        foldersSelected -> onScanNow
                        else -> onChooseFolders
                    },
                    shapes = ButtonDefaults.shapes(),
                    enabled = !scanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        when {
                            scanning -> stringResource(Res.string.home_scanning)
                            pending > 0 -> pluralStringResource(
                                Res.plurals.home_back_up_pending,
                                pending,
                                pending
                            )

                            foldersSelected -> stringResource(Res.string.home_scan_now)
                            else -> stringResource(Res.string.home_choose_folders)
                        }
                    )
                }
            }
        }
    }
}

/** Connection state worth showing, or null while everything is normal. */
private data class ConnectionStatus(
    val indicator: ConnectionIndicator,
    val labelRes: StringResource
)

/**
 * Connectivity is only surfaced when it needs attention. A recovery shows
 * briefly so the change is acknowledged, then the row disappears again.
 */
@Composable
private fun rememberConnectionStatus(
    offline: Boolean,
    connection: TelegramConnectionState,
    known: Boolean
): ConnectionStatus? {
    if (!known) return null
    val settled = !offline && connection == TelegramConnectionState.READY
    var wasSettled by rememberSaveable { mutableStateOf(settled) }
    var showRecovered by remember { mutableStateOf(false) }

    LaunchedEffect(settled) {
        if (settled && !wasSettled) {
            showRecovered = true
            delay(RECOVERED_VISIBLE_MS.milliseconds)
            showRecovered = false
        }
        wasSettled = settled
    }

    return when {
        offline -> ConnectionStatus(
            ConnectionIndicator.OFFLINE,
            Res.string.home_status_offline
        )

        connection == TelegramConnectionState.UPDATING -> ConnectionStatus(
            ConnectionIndicator.WORKING,
            Res.string.home_status_syncing
        )

        connection != TelegramConnectionState.READY -> ConnectionStatus(
            ConnectionIndicator.WORKING,
            Res.string.home_status_connecting
        )

        showRecovered -> ConnectionStatus(
            ConnectionIndicator.CONNECTED,
            Res.string.home_status_connected
        )

        else -> null
    }
}


@Composable
private fun StorageCard(slices: List<StorageSlice>, modifier: Modifier = Modifier) {
    val totalBytes = remember(slices) { slices.sumOf { it.totalBytes } }
    val totalFiles = remember(slices) { slices.sumOf { it.fileCount } }
    if (totalBytes <= 0L) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = Formatters.bytes(totalBytes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pluralStringResource(Res.plurals.file_count, totalFiles, totalFiles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            StorageBar(slices = slices, totalBytes = totalBytes)
            Spacer(Modifier.height(16.dp))
            StorageLegend(slices = slices, totalBytes = totalBytes)
        }
    }
}

/** Proportional bar. Every slice keeps a sliver so nothing vanishes entirely. */
@Composable
private fun StorageBar(slices: List<StorageSlice>, totalBytes: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP)
    ) {
        slices.forEach { slice ->
            val fraction = (slice.totalBytes.toFloat() / totalBytes).coerceAtLeast(MIN_SLICE)
            Box(
                modifier = Modifier
                    .weight(fraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(slice.category.chartColor())
            )
        }
    }
}

private val LegendTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge

@Composable
private fun StorageLegend(slices: List<StorageSlice>, totalBytes: Long) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            slices.forEach { slice ->
                Row(
                    modifier = Modifier.padding(vertical = LEGEND_ROW_GAP),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(LEGEND_DOT)
                            .clip(CircleShape)
                            .background(slice.category.chartColor())
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = slice.category.label(),
                        style = LegendTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            slices.forEach { slice ->
                val percent = slice.totalBytes * 100f / totalBytes
                Text(
                    text = if (percent < 1f) {
                        stringResource(Res.string.home_storage_percent_min)
                    } else {
                        stringResource(Res.string.home_storage_percent, percent.roundToInt())
                    },
                    style = LegendTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = LEGEND_ROW_GAP)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            slices.forEach { slice ->
                Text(
                    text = Formatters.bytes(slice.totalBytes),
                    style = LegendTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = LEGEND_ROW_GAP)
                )
            }
        }
    }
}


/**
 * The backup card answers whether the drive is current, which the storage
 * card cannot say. A stale timestamp means nothing while nothing is
 * scheduled, so a disabled schedule outranks it.
 */
@Composable
private fun backupFreshnessLabel(
    autoBackupEnabled: Boolean,
    lastBackupAt: Long?,
    totalFiles: Int,
    backedUpCount: Int
): String = when {
    !autoBackupEnabled -> stringResource(Res.string.home_backup_auto_off)
    /* Files indexed from an existing channel are already safe even though this
       device never ran a session, so claiming "no backup yet" would be wrong. */
    lastBackupAt == null && totalFiles > 0 && backedUpCount >= totalFiles ->
        stringResource(Res.string.home_backup_all_in_telegram)

    lastBackupAt == null -> stringResource(Res.string.home_backup_never)
    else -> when (val age = Formatters.relativeAge(lastBackupAt)) {
        AgeBucket.JustNow -> stringResource(Res.string.home_backup_just_now)
        is AgeBucket.Minutes -> stringResource(
            Res.string.home_backup_at,
            pluralStringResource(Res.plurals.backup_age_minutes, age.value, age.value)
        )

        is AgeBucket.Hours -> stringResource(
            Res.string.home_backup_at,
            pluralStringResource(Res.plurals.backup_age_hours, age.value, age.value)
        )

        is AgeBucket.Days -> stringResource(
            Res.string.home_backup_at,
            pluralStringResource(Res.plurals.backup_age_days, age.value, age.value)
        )

        is AgeBucket.Longer -> stringResource(
            Res.string.home_backup_at,
            Formatters.date(age.epochMillis)
        )
    }
}


private val AVATAR_SIZE = 32.dp
private const val RECOVERED_VISIBLE_MS = 2_000L
private val BAR_HEIGHT = 14.dp
private val BAR_GAP = 3.dp
private val LEGEND_DOT = 12.dp
private const val MIN_SLICE = 0.02f
private val LEGEND_ROW_GAP = 6.dp
