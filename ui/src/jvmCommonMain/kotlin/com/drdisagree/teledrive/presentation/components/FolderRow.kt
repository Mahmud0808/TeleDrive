package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_content_description_favorite
import com.drdisagree.teledrive.resources.common_content_description_available_offline
import com.drdisagree.teledrive.domain.model.DriveFolder

@Composable
fun FolderRow(
    folder: DriveFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    showFavorite: Boolean = true,
    showAvailableOffline: Boolean = true,
    onLongClick: () -> Unit = {}
) {
    val compact = LocalCompactLayout.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 38.dp else 48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = folder.contentsLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (folder.isFavorite && showFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(Res.string.common_content_description_favorite),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        if (folder.isAvailableOffline && showAvailableOffline) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.OfflinePin,
                contentDescription = stringResource(
                    Res.string.common_content_description_available_offline
                ),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
