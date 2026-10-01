package com.drdisagree.teledrive.presentation.collection

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.collection_offline_downloading
import com.drdisagree.teledrive.resources.collection_offline_not_downloaded
import com.drdisagree.teledrive.resources.common_content_description_available_offline
import org.jetbrains.compose.resources.stringResource

@Composable
fun OfflineFileStatus(
    onDevice: Boolean,
    downloading: Boolean,
    progress: Float?,
    modifier: Modifier = Modifier
) {
    val state = when {
        downloading -> OfflineFileState.DOWNLOADING
        onDevice -> OfflineFileState.ON_DEVICE
        else -> OfflineFileState.MISSING
    }
    Crossfade(targetState = state, modifier = modifier.size(18.dp)) { current ->
        Box(contentAlignment = Alignment.Center) {
            when (current) {
                OfflineFileState.ON_DEVICE -> Icon(
                    imageVector = Icons.Filled.OfflinePin,
                    contentDescription = stringResource(
                        Res.string.common_content_description_available_offline
                    ),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                OfflineFileState.DOWNLOADING -> {
                    val label = stringResource(Res.string.collection_offline_downloading)
                    val ringModifier = Modifier
                        .size(16.dp)
                        .semantics { contentDescription = label }
                    if (progress != null) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = ringModifier,
                            strokeWidth = 2.dp
                        )
                    } else {
                        CircularProgressIndicator(modifier = ringModifier, strokeWidth = 2.dp)
                    }
                }

                OfflineFileState.MISSING -> Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = stringResource(
                        Res.string.collection_offline_not_downloaded
                    ),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
