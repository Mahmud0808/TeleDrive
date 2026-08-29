package com.drdisagree.teledrive.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.desktop.resources.Res
import com.drdisagree.teledrive.desktop.resources.app_name
import com.drdisagree.teledrive.desktop.resources.auth_sign_out
import com.drdisagree.teledrive.desktop.resources.drive_create
import com.drdisagree.teledrive.desktop.resources.drive_download
import com.drdisagree.teledrive.desktop.resources.drive_downloaded
import com.drdisagree.teledrive.desktop.resources.drive_empty
import com.drdisagree.teledrive.desktop.resources.drive_file_count
import com.drdisagree.teledrive.desktop.resources.drive_loading
import com.drdisagree.teledrive.desktop.resources.drive_open
import com.drdisagree.teledrive.desktop.resources.drive_pick_subtitle
import com.drdisagree.teledrive.desktop.resources.drive_pick_title
import com.drdisagree.teledrive.desktop.resources.drive_queued
import com.drdisagree.teledrive.desktop.resources.drive_sync
import com.drdisagree.teledrive.desktop.resources.drive_syncing
import com.drdisagree.teledrive.desktop.resources.drive_upload
import com.drdisagree.teledrive.desktop.resources.drive_upload_chooser_title
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.DriveChannel
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun DriveScreen() {
    val channelRepository = koinInject<ChannelRepository>()
    val fileRepository = koinInject<FileRepository>()
    val syncRepository = koinInject<SyncRepository>()
    val transferRepository = koinInject<TransferRepository>()
    val authRepository = koinInject<TelegramAuthRepository>()
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(DrivePhase.LOADING) }
    val channels by channelRepository.observeChannels().collectAsState(emptyList())
    val files by remember(phase) {
        if (phase == DrivePhase.FILES) fileRepository.observeFiles(FileQuerySpec()) else flowOf(emptyList())
    }.collectAsState(emptyList())

    LaunchedEffect(Unit) {
        channelRepository.refresh()
        if (!channelRepository.activeDriveMissing()) {
            phase = DrivePhase.SYNCING
            syncRepository.syncOnStart()
            phase = DrivePhase.FILES
        } else {
            phase = DrivePhase.PICK
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (phase) {
            DrivePhase.LOADING -> CenteredProgress(stringResource(Res.string.drive_loading))
            DrivePhase.SYNCING -> CenteredProgress(stringResource(Res.string.drive_syncing))

            DrivePhase.PICK -> ChannelPicker(
                channels = channels,
                onOpen = { channel ->
                    scope.launch {
                        phase = DrivePhase.SYNCING
                        channelRepository.switchTo(channel.chatId)
                        syncRepository.fullResync()
                        phase = DrivePhase.FILES
                    }
                },
                onCreate = {
                    scope.launch {
                        phase = DrivePhase.SYNCING
                        channelRepository.create(getString(Res.string.app_name))
                        phase = DrivePhase.FILES
                    }
                }
            )

            DrivePhase.FILES -> FileList(
                files = files,
                onSync = {
                    scope.launch {
                        phase = DrivePhase.SYNCING
                        syncRepository.incrementalSync()
                        phase = DrivePhase.FILES
                    }
                },
                onUpload = {
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { pickFile() } ?: return@launch
                        val imported = fileRepository.importLocalFile(path, null)
                        if (imported is AppResult.Success) {
                            transferRepository.enqueueUpload(imported.value.id)
                        }
                    }
                },
                onDownload = { file ->
                    scope.launch { transferRepository.enqueueDownload(file.id) }
                },
                onSignOut = {
                    scope.launch { authRepository.logout() }
                }
            )
        }
    }
}

private enum class DrivePhase {
    LOADING,
    PICK,
    SYNCING,
    FILES
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CenteredProgress(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularWavyProgressIndicator(modifier = Modifier.size(56.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ChannelPicker(
    channels: List<DriveChannel>,
    onOpen: (DriveChannel) -> Unit,
    onCreate: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(440.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.drive_pick_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(Res.string.drive_pick_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            channels.forEach { channel ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(channel.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(
                                    Res.string.drive_file_count,
                                    channel.remoteFileCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { onOpen(channel) }) {
                            Text(stringResource(Res.string.drive_open))
                        }
                    }
                }
            }
            OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.drive_create))
            }
        }
    }
}

@Composable
private fun FileList(
    files: List<DriveFile>,
    onSync: () -> Unit,
    onUpload: () -> Unit,
    onDownload: (DriveFile) -> Unit,
    onSignOut: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 12.dp)
            )
            Button(onClick = onSync) { Text(stringResource(Res.string.drive_sync)) }
            Button(onClick = onUpload) { Text(stringResource(Res.string.drive_upload)) }
            OutlinedButton(onClick = onSignOut) {
                Text(stringResource(Res.string.auth_sign_out))
            }
        }
        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.drive_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    FileRow(file = file, onDownload = onDownload)
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: DriveFile, onDownload: (DriveFile) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.padding(end = 16.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = formatSize(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                file.hasLocalCopy -> Text(
                    text = stringResource(Res.string.drive_downloaded),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                file.hasRemoteCopy -> OutlinedButton(onClick = { onDownload(file) }) {
                    Text(stringResource(Res.string.drive_download))
                }

                else -> Text(
                    text = stringResource(Res.string.drive_queued),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun pickFile(): String? {
    val dialog = FileDialog(null as Frame?, runBlocking {
        getString(Res.string.drive_upload_chooser_title)
    }, FileDialog.LOAD)
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return File(dialog.directory, file).absolutePath
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes B"
}
