package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.BuildConfig
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.update.AppRelease
import androidx.compose.ui.platform.LocalContext
import com.drdisagree.teledrive.presentation.common.MarkdownText
import com.drdisagree.teledrive.presentation.common.openLink

@Composable
fun UpdateDialog(
    release: AppRelease,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_update_title, release.version)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(
                        R.string.about_update_from,
                        BuildConfig.VERSION_NAME,
                        release.version
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    MarkdownText(
                        text = release.notes,
                        onOpenUrl = { url -> openLink(context, url) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDownload, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.about_update_download))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.about_update_later))
            }
        }
    )
}
