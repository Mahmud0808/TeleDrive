package com.drdisagree.teledrive.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.media.PartedMediaByteSource
import com.drdisagree.teledrive.core.media.TelegramMediaByteSource
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.desktop.media.ExternalMediaPlayer
import com.drdisagree.teledrive.desktop.media.MediaStreamServer
import com.drdisagree.teledrive.desktop.resources.Res as DesktopRes
import com.drdisagree.teledrive.desktop.resources.preview_open_externally
import com.drdisagree.teledrive.desktop.resources.preview_open_failed
import com.drdisagree.teledrive.desktop.resources.preview_stream
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.common.LinkedText
import com.drdisagree.teledrive.presentation.common.load
import com.drdisagree.teledrive.presentation.common.MarkdownText
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.FileInfoSheet
import com.drdisagree.teledrive.presentation.components.ErrorState
import com.drdisagree.teledrive.presentation.components.LoadingState
import com.drdisagree.teledrive.presentation.components.RenameDialog
import com.drdisagree.teledrive.presentation.platform.LocalFileRevealer
import com.drdisagree.teledrive.presentation.platform.LocalUrlOpener
import com.drdisagree.teledrive.presentation.preview.PreviewContent
import com.drdisagree.teledrive.presentation.preview.PreviewViewModel
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_actions
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_download
import com.drdisagree.teledrive.resources.common_free_space
import com.drdisagree.teledrive.resources.common_move_trash
import com.drdisagree.teledrive.resources.common_rename
import com.drdisagree.teledrive.resources.common_rename_file
import com.drdisagree.teledrive.resources.common_upload
import com.drdisagree.teledrive.resources.files_hide
import com.drdisagree.teledrive.resources.files_show_in_file_manager
import com.drdisagree.teledrive.resources.files_unhide
import com.drdisagree.teledrive.resources.preview_add_favorites
import com.drdisagree.teledrive.resources.preview_archive
import com.drdisagree.teledrive.resources.preview_confirm_trash_file_message
import com.drdisagree.teledrive.resources.preview_file_info
import com.drdisagree.teledrive.resources.preview_move_to_trash
import com.drdisagree.teledrive.resources.preview_next
import com.drdisagree.teledrive.resources.preview_no_preview
import com.drdisagree.teledrive.resources.preview_preparing
import com.drdisagree.teledrive.resources.preview_previous
import com.drdisagree.teledrive.resources.preview_progress_bytes
import com.drdisagree.teledrive.resources.preview_remove_favorites
import com.drdisagree.teledrive.resources.preview_requires_download
import com.drdisagree.teledrive.resources.preview_download_view
import com.drdisagree.teledrive.resources.preview_truncated_download_view
import com.drdisagree.teledrive.resources.preview_unarchive
import java.awt.Desktop
import java.io.File
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Desktop preview renders what the shared resolver can produce inline and
 * hands everything else to the system's own viewer. Media never plays inside
 * the window; a local copy opens externally instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesktopPreviewScreen(
    onBack: () -> Unit,
    viewModel: PreviewViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.closed) { if (state.closed) onBack() }

    var index by remember(state.ready) { mutableStateOf(state.initialIndex) }
    val file = state.files.getOrNull(index.coerceIn(0, (state.files.size - 1).coerceAtLeast(0)))
    val infoTarget by viewModel.infoTarget.collectAsState()
    var showOverflow by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DriveFile?>(null) }
    var trashTarget by remember { mutableStateOf<DriveFile?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.load()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = file?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
                        )
                    }
                },
                actions = {
                    file?.let { currentFile ->
                        IconButton(onClick = { viewModel.showInfo(currentFile) }) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = stringResource(Res.string.preview_file_info)
                            )
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(Res.string.common_actions)
                                )
                            }
                            PreviewOverflowMenu(
                                expanded = showOverflow,
                                file = currentFile,
                                viewModel = viewModel,
                                onDismiss = { showOverflow = false },
                                onRename = { renameTarget = it },
                                onTrash = { trashTarget = it }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(state.ready) { if (state.ready) focusRequester.requestFocus() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (index > 0) index--
                            true
                        }

                        Key.DirectionRight -> {
                            if (index < state.files.lastIndex) index++
                            true
                        }

                        else -> false
                    }
                }
        ) {
            when {
                !state.ready || file == null -> LoadingState()
                else -> AnimatedContent(
                    targetState = index to file,
                    transitionSpec = {
                        val forward = targetState.first >= initialState.first
                        val enter = slideInHorizontally { width ->
                            if (forward) width else -width
                        } + fadeIn()
                        val exit = slideOutHorizontally { width ->
                            if (forward) -width else width
                        } + fadeOut()
                        enter togetherWith exit
                    },
                    contentKey = { it.second.id }
                ) { (_, pageFile) ->
                    PreviewPane(
                        viewModel = viewModel,
                        file = pageFile,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
            if (state.files.size > 1) {
                PreviewPagerButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(Res.string.preview_previous),
                    enabled = index > 0,
                    onClick = { index-- },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                )
                PreviewPagerButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(Res.string.preview_next),
                    enabled = index < state.files.lastIndex,
                    onClick = { index++ },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                )
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            title = stringResource(Res.string.common_rename_file),
            initialValue = target.name,
            confirmLabel = stringResource(Res.string.common_rename),
            onConfirm = {
                viewModel.rename(target, it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
    trashTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(Res.string.preview_move_to_trash),
            message = stringResource(Res.string.preview_confirm_trash_file_message, target.name),
            confirmLabel = stringResource(Res.string.common_move_trash),
            destructive = true,
            onConfirm = {
                viewModel.moveToTrash(target)
                trashTarget = null
            },
            onDismiss = { trashTarget = null }
        )
    }
    infoTarget?.let { target ->
        FileInfoSheet(file = target, onDismiss = viewModel::dismissInfo)
    }
}

@Composable
private fun PreviewOverflowMenu(
    expanded: Boolean,
    file: DriveFile,
    viewModel: PreviewViewModel,
    onDismiss: () -> Unit,
    onRename: (DriveFile) -> Unit,
    onTrash: (DriveFile) -> Unit
) {
    val fileRevealer = LocalFileRevealer.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Text(
                    if (file.isFavorite) stringResource(Res.string.preview_remove_favorites)
                    else stringResource(Res.string.preview_add_favorites)
                )
            },
            onClick = {
                onDismiss()
                viewModel.setFavorite(file, !file.isFavorite)
            }
        )
        val localPath = file.localPath
        if (fileRevealer != null && localPath != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.files_show_in_file_manager)) },
                enabled = remember(localPath) { File(localPath).exists() },
                onClick = {
                    onDismiss()
                    fileRevealer.reveal(localPath)
                }
            )
        }
        if (file.hasRemoteCopy && !file.hasLocalCopy) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.common_download)) },
                onClick = {
                    onDismiss()
                    viewModel.download(file)
                }
            )
        }
        if (!file.hasRemoteCopy) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.common_upload)) },
                onClick = {
                    onDismiss()
                    viewModel.upload(file)
                }
            )
        }
        if (file.hasRemoteCopy && file.hasLocalCopy) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.common_free_space)) },
                onClick = {
                    onDismiss()
                    viewModel.freeUpSpace(file)
                }
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    if (file.isHidden) stringResource(Res.string.files_unhide)
                    else stringResource(Res.string.files_hide)
                )
            },
            onClick = {
                onDismiss()
                viewModel.setHidden(file, !file.isHidden)
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (file.isArchived) stringResource(Res.string.preview_unarchive)
                    else stringResource(Res.string.preview_archive)
                )
            },
            onClick = {
                onDismiss()
                viewModel.setArchived(file, !file.isArchived)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.common_rename)) },
            onClick = {
                onDismiss()
                onRename(file)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.common_move_trash)) },
            onClick = {
                onDismiss()
                onTrash(file)
            }
        )
    }
}

@Composable
private fun PreviewPagerButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f)
        )
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewPane(
    viewModel: PreviewViewModel,
    file: DriveFile,
    snackbarHostState: SnackbarHostState
) {
    val content by viewModel.contentFor(file).collectAsState()
    val urlOpener = LocalUrlOpener.current

    when (val current = content) {
        is PreviewContent.Loading -> LoadingState()

        is PreviewContent.DownloadProgress -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.preview_preparing, file.name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            if (current.total > 0) {
                LinearWavyProgressIndicator(
                    progress = {
                        (current.transferred.toFloat() / current.total).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = stringResource(
                    Res.string.preview_progress_bytes,
                    Formatters.bytes(current.transferred),
                    Formatters.bytes(current.total)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        is PreviewContent.Image -> AsyncImage(
            model = current.model,
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        is PreviewContent.LocalMedia -> OpenExternallyState(
            icon = if (current.isAudio) Icons.Filled.Audiotrack else Icons.Filled.Movie,
            file = file,
            path = current.path,
            snackbarHostState = snackbarHostState
        )

        is PreviewContent.Pdf -> OpenExternallyState(
            icon = Icons.Filled.PictureAsPdf,
            file = file,
            path = current.path,
            snackbarHostState = snackbarHostState
        )

        is PreviewContent.StreamedMedia -> StreamState(
            file = file,
            content = current,
            onDownload = { viewModel.download(file) },
            snackbarHostState = snackbarHostState
        )

        is PreviewContent.PlainText -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            if (MimeTypes.isMarkdown(file.mimeType)) {
                MarkdownText(
                    text = current.text,
                    onOpenUrl = { url -> urlOpener.open(url) }
                )
            } else {
                LinkedText(
                    text = current.text,
                    style = MaterialTheme.typography.bodySmall
                        .copy(fontFamily = FontFamily.Monospace)
                        .copy(color = MaterialTheme.colorScheme.onSurface),
                    linkColor = MaterialTheme.colorScheme.primary,
                    onOpenUrl = { url -> urlOpener.open(url) }
                )
            }
            if (current.truncated) {
                Text(
                    text = stringResource(Res.string.preview_truncated_download_view),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is PreviewContent.Archive -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            items(current.entries) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) {
                            Icons.Filled.Folder
                        } else {
                            Icons.AutoMirrored.Filled.InsertDriveFile
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!entry.isDirectory) {
                        Text(
                            text = Formatters.bytes(entry.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is PreviewContent.RequiresDownload -> DownloadState(
            sizeBytes = current.sizeBytes,
            onDownload = { viewModel.download(file) }
        )

        is PreviewContent.Unsupported -> EmptyState(
            icon = Icons.Outlined.Description,
            title = stringResource(Res.string.preview_no_preview),
            description = stringResource(current.reasonRes),
            actionLabel = if (file.hasLocalCopy) null else stringResource(Res.string.common_download),
            onAction = if (file.hasLocalCopy) null else ({ viewModel.download(file) })
        )

        is PreviewContent.Failed -> ErrorState(message = stringResource(current.messageRes))
    }
}

@Composable
private fun OpenExternallyState(
    icon: ImageVector,
    file: DriveFile,
    path: String,
    snackbarHostState: SnackbarHostState
) {
    var openFailed by remember(path) { mutableStateOf(false) }
    if (openFailed) {
        LaunchedEffect(path) {
            snackbarHostState.showSnackbar(getString(DesktopRes.string.preview_open_failed))
            openFailed = false
        }
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = Formatters.bytes(file.sizeBytes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { if (!openExternally(path)) openFailed = true }) {
            Text(stringResource(DesktopRes.string.preview_open_externally))
        }
    }
}

@Composable
private fun StreamState(
    file: DriveFile,
    content: PreviewContent.StreamedMedia,
    onDownload: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val streamServer = koinInject<MediaStreamServer>()
    val player = koinInject<ExternalMediaPlayer>()
    val telegramClient = koinInject<TelegramClient>()
    val streamCrypto = koinInject<StreamCrypto>()
    val wrappedKeyRepository = koinInject<WrappedKeyRepository>()

    var playFailed by remember(file.id) { mutableStateOf(false) }
    if (playFailed) {
        LaunchedEffect(file.id) {
            snackbarHostState.showSnackbar(getString(DesktopRes.string.preview_open_failed))
            playFailed = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (content.isAudio) Icons.Filled.Audiotrack else Icons.Filled.Movie,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = Formatters.bytes(file.sizeBytes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = {
            val url = streamServer.serve(file.name, file.mimeType) {
                if (content.parts.isEmpty()) {
                    TelegramMediaByteSource(telegramClient, content.remoteFileId)
                } else {
                    PartedMediaByteSource(
                        telegramClient = telegramClient,
                        streamCrypto = streamCrypto,
                        parts = content.parts,
                        encrypted = content.encrypted,
                        contentKey = if (content.encrypted) {
                            wrappedKeyRepository.get(CryptoKeys.CONTENT)
                        } else {
                            null
                        }
                    )
                }
            }
            if (!player.play(url)) playFailed = true
        }) {
            Text(stringResource(DesktopRes.string.preview_stream))
        }
        OutlinedButton(onClick = onDownload) {
            Text(stringResource(Res.string.common_download))
        }
    }
}

@Composable
private fun DownloadState(sizeBytes: Long, onDownload: () -> Unit) {
    EmptyState(
        icon = Icons.Filled.Download,
        title = stringResource(Res.string.preview_download_view),
        description = stringResource(
            Res.string.preview_requires_download,
            Formatters.bytes(sizeBytes)
        ),
        actionLabel = stringResource(Res.string.common_download),
        onAction = onDownload
    )
}

private fun openExternally(path: String): Boolean = runCatching {
    val target = File(path)
    check(target.exists())
    Desktop.getDesktop().open(target)
}.isSuccess
