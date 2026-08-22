package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.drdisagree.teledrive.core.media.ThumbnailModel
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.core.files.MimeTypes

/**
 * Thumbnail with an icon fallback. Image, video and text files attempt
 * thumbnail loading; everything else renders its category icon on a tonal
 * background.
 * Videos carry a play badge so they are distinguishable from stills.
 */
@Composable
fun FileThumbnail(
    file: DriveFile,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    // Text can carry one too: a note holding a link previews as that link.
    val supportsThumbnail = file.category == FileCategory.IMAGE ||
        file.category == FileCategory.VIDEO ||
        MimeTypes.isText(file.mimeType)
    var failed by remember(file.id) { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        val shortestSide = minOf(maxWidth, maxHeight)
        val badgeSize = (shortestSide * BADGE_RATIO).coerceIn(18.dp, 44.dp)
        val showDuration = shortestSide >= DURATION_MIN_SIDE

        if (supportsThumbnail && (file.hasLocalCopy || file.hasRemoteCopy) && !failed) {
            AsyncImage(
                model = ThumbnailModel(file.id),
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onError = { failed = true }
            )
        } else {
            Icon(
                imageVector = iconFor(file.category),
                contentDescription = file.name,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (file.category == FileCategory.VIDEO) {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .background(color = SCRIM, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(badgeSize * 0.66f),
                    tint = Color.White
                )
            }
            file.durationMs?.takeIf { it > 0 && showDuration }?.let { duration ->
                Text(
                    text = Formatters.duration(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(color = SCRIM, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

private val SCRIM = Color.Black.copy(alpha = 0.45f)
private const val BADGE_RATIO = 0.34f
private val DURATION_MIN_SIDE = 72.dp
