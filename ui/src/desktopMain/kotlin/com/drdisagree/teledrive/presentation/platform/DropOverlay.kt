package com.drdisagree.teledrive.presentation.platform

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.files_drop_folders_unsupported
import com.drdisagree.teledrive.resources.files_drop_to_upload
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DropOverlay(indication: DropIndication, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = indication != DropIndication.NONE,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val rejected = indication == DropIndication.FOLDERS
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (rejected) {
                            Icons.Filled.FolderOff
                        } else {
                            Icons.Filled.FileUpload
                        },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = if (rejected) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = stringResource(
                            if (rejected) {
                                Res.string.files_drop_folders_unsupported
                            } else {
                                Res.string.files_drop_to_upload
                            }
                        ),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
