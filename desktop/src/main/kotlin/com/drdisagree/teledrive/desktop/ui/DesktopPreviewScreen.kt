package com.drdisagree.teledrive.desktop.ui

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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
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
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.desktop.resources.Res as DesktopRes
import com.drdisagree.teledrive.desktop.resources.preview_open_externally
import com.drdisagree.teledrive.desktop.resources.preview_open_failed
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.common.LinkedText
import com.drdisagree.teledrive.presentation.common.load
import com.drdisagree.teledrive.presentation.common.MarkdownText
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.ErrorState
import com.drdisagree.teledrive.presentation.components.LoadingState
import com.drdisagree.teledrive.presentation.platform.LocalUrlOpener
import com.drdisagree.teledrive.presentation.preview.PreviewContent
import com.drdisagree.teledrive.presentation.preview.PreviewViewModel
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_download
import com.drdisagree.teledrive.resources.preview_next
import com.drdisagree.teledrive.resources.preview_no_preview
import com.drdisagree.teledrive.resources.preview_preparing
import com.drdisagree.teledrive.resources.preview_previous
import com.drdisagree.teledrive.resources.preview_progress_bytes
import com.drdisagree.teledrive.resources.preview_requires_download
import com.drdisagree.teledrive.resources.preview_download_view
import com.drdisagree.teledrive.resources.preview_truncated_download_view
import java.awt.Desktop
import java.io.File
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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
                else -> PreviewPane(
                    viewModel = viewModel,
                    file = file,
                    snackbarHostState = snackbarHostState
                )
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

        is PreviewContent.StreamedMedia -> DownloadState(
            sizeBytes = file.sizeBytes,
            onDownload = { viewModel.download(file) }
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
