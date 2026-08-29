package com.drdisagree.teledrive.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.desktop.resources.Res
import com.drdisagree.teledrive.desktop.resources.preview_download_first
import com.drdisagree.teledrive.desktop.resources.preview_open_externally
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.preview.PreviewViewModel
import com.drdisagree.teledrive.resources.Res as SharedRes
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_download
import java.awt.Desktop
import java.io.File
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Desktop preview hands files to the system's own viewer instead of rendering
 * media inline: local copies open directly, remote-only files offer a download.
 */
@Composable
fun DesktopPreviewScreen(
    onBack: () -> Unit,
    viewModel: PreviewViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val file = state.files.getOrNull(state.initialIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(SharedRes.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            file ?: return@Column
            Text(file.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = Formatters.bytes(file.sizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val localPath = file.localPath
            if (localPath != null) {
                Button(onClick = {
                    runCatching { Desktop.getDesktop().open(File(localPath)) }
                }) {
                    Text(stringResource(Res.string.preview_open_externally))
                }
            } else {
                Text(
                    text = stringResource(Res.string.preview_download_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { viewModel.download(file) }) {
                    Text(stringResource(SharedRes.string.common_download))
                }
            }
        }
    }
}
