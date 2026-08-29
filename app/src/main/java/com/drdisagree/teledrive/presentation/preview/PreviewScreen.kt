package com.drdisagree.teledrive.presentation.preview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.file_count
import com.drdisagree.teledrive.resources.folder_count
import com.drdisagree.teledrive.resources.item_count
import com.drdisagree.teledrive.resources.preview_share_chooser_title
import com.drdisagree.teledrive.resources.common_actions
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_collapse
import com.drdisagree.teledrive.resources.common_download
import com.drdisagree.teledrive.resources.common_expand
import com.drdisagree.teledrive.resources.common_free_space
import com.drdisagree.teledrive.resources.common_move_trash
import com.drdisagree.teledrive.resources.common_rename
import com.drdisagree.teledrive.resources.common_rename_file
import com.drdisagree.teledrive.resources.common_upload
import com.drdisagree.teledrive.resources.note_edit_action
import com.drdisagree.teledrive.resources.preview_add_favorites
import com.drdisagree.teledrive.resources.preview_archive
import com.drdisagree.teledrive.resources.preview_archive_format_title
import com.drdisagree.teledrive.resources.preview_archive_packed_summary
import com.drdisagree.teledrive.resources.preview_archive_packed_summary_saved
import com.drdisagree.teledrive.resources.preview_chevron
import com.drdisagree.teledrive.resources.preview_confirm_trash_file_message
import com.drdisagree.teledrive.resources.preview_download_view
import com.drdisagree.teledrive.resources.preview_extraction_supported_yet_download
import com.drdisagree.teledrive.resources.preview_file_info
import com.drdisagree.teledrive.resources.preview_move_to_trash
import com.drdisagree.teledrive.resources.preview_no_preview
import com.drdisagree.teledrive.resources.preview_preparing
import com.drdisagree.teledrive.resources.preview_progress_bytes
import com.drdisagree.teledrive.resources.preview_remove_favorites
import com.drdisagree.teledrive.resources.preview_requires_download
import com.drdisagree.teledrive.resources.preview_share_copy
import com.drdisagree.teledrive.resources.preview_truncated_download_view
import com.drdisagree.teledrive.resources.preview_unarchive
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.media.TelegramDataSourceFactory
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.LinkMetadata
import com.drdisagree.teledrive.presentation.common.CollectSnackbarMessages
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.common.LinkedText
import com.drdisagree.teledrive.presentation.common.MarkdownText
import com.drdisagree.teledrive.presentation.common.add
import com.drdisagree.teledrive.presentation.common.normalizeUrl
import com.drdisagree.teledrive.presentation.common.scaledBy
import com.drdisagree.teledrive.presentation.common.soleUrlOf
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.ErrorState
import com.drdisagree.teledrive.presentation.components.FileInfoSheet
import com.drdisagree.teledrive.presentation.components.LoadingState
import com.drdisagree.teledrive.presentation.components.RenameDialog
import com.drdisagree.teledrive.presentation.components.iconFor
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    onEditNote: (String, String) -> Unit,
    viewModel: PreviewViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val infoTarget by viewModel.infoTarget.collectAsStateWithLifecycle()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsStateWithLifecycle()
    val storedTextScale by viewModel.textScale.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val shareChooserTitle = stringResource(Res.string.preview_share_chooser_title)
    val dataSourceFactory = koinInject<TelegramDataSourceFactory>()

    var renameTarget by remember { mutableStateOf<DriveFile?>(null) }
    var deleteTarget by remember { mutableStateOf<DriveFile?>(null) }
    var showOverflow by remember { mutableStateOf(false) }

    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.retryLocalCopyRemoval()
    }

    CollectSnackbarMessages(viewModel.messages, snackbarHostState)
    LaunchedEffect(Unit) {
        viewModel.deleteConsentRequests.collect { request ->
            deleteConsentLauncher.launch(IntentSenderRequest.Builder(request).build())
        }
    }
    LaunchedEffect(state.closed) {
        if (state.closed) onBack()
    }

    if (!state.ready) {
        LoadingState()
        return
    }
    if (state.files.isEmpty()) {
        onBack()
        return
    }

    val pagerState = rememberPagerState(
        initialPage = state.initialIndex.coerceAtMost(state.files.lastIndex),
        pageCount = { state.files.size }
    )
    val pagedFile = state.files[pagerState.currentPage.coerceAtMost(state.files.lastIndex)]
    /* The pager holds a snapshot, so the open row is read live: a rename or an
       edit made elsewhere shows without reopening. */
    val liveFile by viewModel.observeFile(pagedFile.id)
        .collectAsStateWithLifecycle(initialValue = pagedFile)
    val currentFile = liveFile ?: pagedFile

    val immersive = currentFile.category == FileCategory.IMAGE ||
            currentFile.category == FileCategory.VIDEO
    var chromeVisible by remember { mutableStateOf(true) }
    val chromeShown = chromeVisible || !immersive

    val view = LocalView.current
    val activity = LocalActivity.current

    LaunchedEffect(chromeShown, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeShown) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            WindowInsetsControllerCompat(window, view)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                key = { state.files[it].id }
            ) { page ->
                val file = state.files[page]
                val content by viewModel.contentFor(file).collectAsStateWithLifecycle()
                PreviewPage(
                    loadLinkMetadata = { url -> viewModel.linkPreview(url) },
                    storedTextScale = storedTextScale,
                    onTextScaleChange = viewModel::setTextScale,
                    file = file,
                    content = content,
                    isActivePage = pagerState.currentPage == page,
                    dataSourceFactory = dataSourceFactory,
                    onDownload = { viewModel.download(file) },
                    chromeVisible = chromeVisible,
                    allowBackgroundPlayback = backgroundPlayback,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onChromeRequested = { visible -> chromeVisible = visible }
                )
            }

            AnimatedVisibility(
                visible = chromeShown,
                enter = slideInVertically { height -> -height } + fadeIn(),
                exit = slideOutVertically { height -> -height } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            currentFile.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
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
                        // Only text has anything the note editor can open.
                        if (MimeTypes.isText(currentFile.mimeType)) {
                            IconButton(
                                onClick = {
                                    onEditNote(
                                        currentFile.id,
                                        currentFile.name.substringBeforeLast('.')
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(Res.string.note_edit_action)
                                )
                            }
                        }
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
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (currentFile.isFavorite) stringResource(Res.string.preview_remove_favorites)
                                            else stringResource(Res.string.preview_add_favorites)
                                        )
                                    },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.setFavorite(
                                            currentFile,
                                            !currentFile.isFavorite
                                        )
                                    }
                                )
                                if (currentFile.hasLocalCopy) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.preview_share_copy)) },
                                        onClick = {
                                            showOverflow = false
                                            shareFile(context, currentFile, shareChooserTitle)
                                        }
                                    )
                                }
                                if (currentFile.hasRemoteCopy && !currentFile.hasLocalCopy) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.common_download)) },
                                        onClick = {
                                            showOverflow = false
                                            viewModel.download(currentFile)
                                        }
                                    )
                                }
                                if (!currentFile.hasRemoteCopy) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.common_upload)) },
                                        onClick = {
                                            showOverflow = false
                                            viewModel.upload(currentFile)
                                        }
                                    )
                                }
                                if (currentFile.hasRemoteCopy && currentFile.hasLocalCopy) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.common_free_space)) },
                                        onClick = {
                                            showOverflow = false
                                            viewModel.freeUpSpace(currentFile)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(if (currentFile.isHidden) "Unhide" else "Hide")
                                    },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.setHidden(currentFile, !currentFile.isHidden)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (currentFile.isArchived) stringResource(Res.string.preview_unarchive)
                                            else stringResource(Res.string.preview_archive)
                                        )
                                    },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.setArchived(
                                            currentFile,
                                            !currentFile.isArchived
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.common_rename)) },
                                    onClick = {
                                        showOverflow = false
                                        renameTarget = currentFile
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.common_move_trash)) },
                                    onClick = {
                                        showOverflow = false
                                        deleteTarget = currentFile
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    )
                )
            }
        }
    }

    renameTarget?.let { file ->
        RenameDialog(
            title = stringResource(Res.string.common_rename_file),
            initialValue = file.name,
            confirmLabel = stringResource(Res.string.common_rename),
            onConfirm = {
                viewModel.rename(file, it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { file ->
        ConfirmDialog(
            title = stringResource(Res.string.preview_move_to_trash),
            message = stringResource(Res.string.preview_confirm_trash_file_message, file.name),
            confirmLabel = stringResource(Res.string.common_move_trash),
            destructive = true,
            onConfirm = {
                viewModel.moveToTrash(file)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
    infoTarget?.let { file ->
        FileInfoSheet(file = file, onDismiss = viewModel::dismissInfo)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewPage(
    file: DriveFile,
    content: PreviewContent,
    isActivePage: Boolean,
    dataSourceFactory: TelegramDataSourceFactory,
    onDownload: () -> Unit,
    chromeVisible: Boolean,
    allowBackgroundPlayback: Boolean,
    onToggleChrome: () -> Unit,
    onChromeRequested: (Boolean) -> Unit,
    loadLinkMetadata: suspend (String) -> LinkMetadata?,
    storedTextScale: Float,
    onTextScaleChange: (Float) -> Unit
) {
    /* One state for the screen's life: re-creating it on save would leave the
       gesture handler writing to a state nothing renders. */
    var textScale by remember { mutableFloatStateOf(storedTextScale) }
    LaunchedEffect(storedTextScale) {
        if (storedTextScale != textScale) textScale = storedTextScale
    }
    val pinchToScale = Modifier.pointerInput(file.id) {
        detectTransformGestures { _, _, zoom, _ ->
            textScale = (textScale * zoom).coerceIn(TEXT_SCALE_MIN, TEXT_SCALE_MAX)
        }
    }
    // Written once the pinch settles, not on every frame of the gesture.
    LaunchedEffect(textScale) {
        if (textScale != storedTextScale) {
            delay(TEXT_SCALE_SAVE_DELAY_MS.milliseconds)
            onTextScaleChange(textScale)
        }
    }
    when (content) {
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
            if (content.total > 0) {
                LinearWavyProgressIndicator(
                    progress = {
                        (content.transferred.toFloat() / content.total).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = stringResource(
                    Res.string.preview_progress_bytes,
                    Formatters.bytes(content.transferred),
                    Formatters.bytes(content.total)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        is PreviewContent.Image -> ZoomableImage(
            model = content.model,
            contentDescription = file.name,
            onTap = onToggleChrome
        )

        is PreviewContent.LocalMedia,
        is PreviewContent.StreamedMedia ->
            MediaPlayer(
                content = content,
                title = file.name,
                fileId = file.id,
                dataSourceFactory = dataSourceFactory,
                isActivePage = isActivePage,
                controlsVisible = chromeVisible,
                allowBackgroundPlayback = allowBackgroundPlayback,
                onControlsVisibilityChanged = onChromeRequested
            )

        is PreviewContent.Pdf -> PdfPreview(path = content.path)
        is PreviewContent.PlainText -> if (soleUrlOf(content.text) != null) {
            val url = normalizeUrl(soleUrlOf(content.text).orEmpty())
            val linkContext = LocalContext.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .then(pinchToScale)
                    .padding(previewContentPadding())
            ) {
                SavedLink(
                    textScale = textScale,
                    url = url,
                    onOpen = { openUrl(linkContext, url) },
                    loadMetadata = loadLinkMetadata
                )
            }
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(pinchToScale)
                .padding(previewContentPadding())
        ) {
            val linkContext = LocalContext.current
            if (MimeTypes.isMarkdown(file.mimeType)) {
                MarkdownText(
                    text = content.text,
                    textScale = textScale,
                    onOpenUrl = { url -> openUrl(linkContext, url) }
                )
            } else {
                LinkedText(
                    text = content.text,
                    style = MaterialTheme.typography.bodySmall
                        .copy(fontFamily = FontFamily.Monospace)
                        .copy(color = MaterialTheme.colorScheme.onSurface)
                        .scaledBy(textScale),
                    linkColor = MaterialTheme.colorScheme.primary,
                    onOpenUrl = { url -> openUrl(linkContext, url) }
                )
            }
            if (content.truncated) {
                Text(
                    text = stringResource(Res.string.preview_truncated_download_view),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is PreviewContent.Archive -> ArchiveList(content)
        is PreviewContent.RequiresDownload -> EmptyState(
            icon = Icons.Filled.Download,
            title = stringResource(Res.string.preview_download_view),
            description = stringResource(
                Res.string.preview_requires_download,
                Formatters.bytes(content.sizeBytes)
            ),
            actionLabel = stringResource(Res.string.common_download),
            onAction = onDownload
        )

        is PreviewContent.Unsupported -> EmptyState(
            icon = Icons.Outlined.Description,
            title = stringResource(Res.string.preview_no_preview),
            description = stringResource(content.reasonRes),
            actionLabel = if (file.hasLocalCopy) null else stringResource(Res.string.common_download),
            onAction = if (file.hasLocalCopy) null else onDownload
        )

        is PreviewContent.Failed -> ErrorState(message = stringResource(content.messageRes))
    }
}

/**
 * Content in the viewer draws edge to edge under the overlaid toolbar and the
 * system bars, so scrollable previews pad themselves instead of being clipped.
 */
@Composable
private fun previewContentPadding(): PaddingValues =
    WindowInsets.systemBars
        .asPaddingValues()
        .add(horizontal = 16.dp, top = PreviewTopBarHeight + 8.dp, bottom = 16.dp)

@Composable
private fun ArchiveList(archive: PreviewContent.Archive) {
    val root = remember(archive) { buildArchiveTree(archive.entries) }
    var expanded by remember(archive) { mutableStateOf(emptySet<String>()) }
    val rows = remember(root, expanded) { flattenArchive(root, expanded) }
    val files = remember(archive) { archive.entries.filterNot { it.isDirectory } }
    val folderCount = remember(root) { countFolders(root) }
    val originalBytes = remember(files) { files.sumOf { it.sizeBytes } }
    val storedBytes = remember(files) { files.sumOf { it.compressedBytes } }
    val saved = remember(originalBytes, storedBytes) {
        if (originalBytes <= 0) 0 else (100 - storedBytes * 100 / originalBytes).toInt()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = previewContentPadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(
                            Res.string.preview_archive_format_title,
                            archive.format
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = buildString {
                            append(
                                pluralStringResource(
                                    Res.plurals.file_count,
                                    files.size,
                                    files.size
                                )
                            )
                            if (folderCount > 0) {
                                append(", ")
                                append(
                                    pluralStringResource(
                                        Res.plurals.folder_count,
                                        folderCount,
                                        folderCount
                                    )
                                )
                            }
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = if (saved > 0) {
                            stringResource(
                                Res.string.preview_archive_packed_summary_saved,
                                Formatters.bytes(storedBytes),
                                Formatters.bytes(originalBytes),
                                saved
                            )
                        } else {
                            stringResource(
                                Res.string.preview_archive_packed_summary,
                                Formatters.bytes(storedBytes),
                                Formatters.bytes(originalBytes)
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        items(rows, key = { it.node.path }) { row ->
            ArchiveRow(
                row = row,
                onToggle = {
                    expanded = if (row.node.path in expanded) {
                        expanded - row.node.path
                    } else {
                        expanded + row.node.path
                    }
                },
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
        item {
            Text(
                text = stringResource(Res.string.preview_extraction_supported_yet_download),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun ArchiveRow(
    row: ArchiveRowState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val node = row.node
    val icon = if (node.isDirectory) {
        if (row.expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
    } else {
        iconFor(FileCategory.fromMimeType(MimeTypes.fromFileName(node.name)))
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (row.expanded) 180f else 0f,
        animationSpec = tween(CHEVRON_ROTATE_MS),
        label = stringResource(Res.string.preview_chevron)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = INDENT_STEP * row.depth)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (node.isDirectory) Modifier.clickable { onToggle() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (node.isDirectory) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (node.isDirectory) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (node.isDirectory) {
                    val items = node.children.size
                    val label = pluralStringResource(Res.plurals.item_count, items, items)
                    "$label · ${Formatters.bytes(node.sizeBytes)}"
                } else {
                    Formatters.bytes(node.sizeBytes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (node.isDirectory) {
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (row.expanded) stringResource(Res.string.common_collapse) else stringResource(
                    Res.string.common_expand
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation)
            )
        } else {
            Text(
                text = Formatters.bytes(node.compressedBytes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * ZIP stores a flat list of paths, so the tree is derived from the names.
 * Folders without an entry of their own are created from the paths below them.
 */
private fun buildArchiveTree(entries: List<PreviewContent.ArchiveEntry>): ArchiveNode {
    val root = ArchiveNode(path = "", name = "", isDirectory = true)
    for (entry in entries) {
        val segments = entry.name.split(SEPARATOR).filter { it.isNotEmpty() }
        if (segments.isEmpty()) continue
        var current = root
        segments.forEachIndexed { index, segment ->
            val leaf = index == segments.lastIndex && !entry.isDirectory
            val path = if (current.path.isEmpty()) segment else current.path + SEPARATOR + segment
            val child = current.children.getOrPut(segment) {
                ArchiveNode(path = path, name = segment, isDirectory = !leaf)
            }
            if (leaf) {
                child.sizeBytes = entry.sizeBytes
                child.compressedBytes = entry.compressedBytes
            }
            current = child
        }
    }
    aggregateSizes(root)
    return root
}

private fun aggregateSizes(node: ArchiveNode): Pair<Long, Long> {
    if (!node.isDirectory) return node.sizeBytes to node.compressedBytes
    var size = 0L
    var packed = 0L
    node.children.values.forEach { child ->
        val (childSize, childPacked) = aggregateSizes(child)
        size += childSize
        packed += childPacked
    }
    node.sizeBytes = size
    node.compressedBytes = packed
    return size to packed
}

private fun countFolders(node: ArchiveNode): Int =
    node.children.values.sumOf { child ->
        if (child.isDirectory) 1 + countFolders(child) else 0
    }

private fun flattenArchive(
    root: ArchiveNode,
    expanded: Set<String>,
    depth: Int = 0,
    out: MutableList<ArchiveRowState> = mutableListOf()
): List<ArchiveRowState> {
    val sorted = root.children.values.sortedWith(
        compareByDescending<ArchiveNode> { it.isDirectory }
            .thenBy { it.name.lowercase() }
    )
    for (child in sorted) {
        val isOpen = child.isDirectory && child.path in expanded
        out.add(ArchiveRowState(child, depth, isOpen))
        if (isOpen) flattenArchive(child, expanded, depth + 1, out)
    }
    return out
}

private class ArchiveNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    var sizeBytes: Long = 0,
    var compressedBytes: Long = 0,
    val children: LinkedHashMap<String, ArchiveNode> = LinkedHashMap()
)

private data class ArchiveRowState(
    val node: ArchiveNode,
    val depth: Int,
    val expanded: Boolean
)

private const val SEPARATOR = "/"
private const val ROW_FADE_MS = 180
private const val CHEVRON_ROTATE_MS = 220
private val INDENT_STEP = 16.dp

private fun shareFile(context: Context, file: DriveFile, chooserTitle: String) {
    val localPath = file.localPath ?: return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(localPath)
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = file.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(
            intent,
            chooserTitle
        )
    )
}

/** Height the overlaid preview toolbar covers at the top of the screen. */
val PreviewTopBarHeight = 64.dp

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

private const val TEXT_SCALE_MIN = 0.7f
private const val TEXT_SCALE_MAX = 3f
private const val TEXT_SCALE_SAVE_DELAY_MS = 400L
