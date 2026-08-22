package com.drdisagree.teledrive.presentation.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.domain.model.LinkMetadata
import com.drdisagree.teledrive.presentation.common.scaledBy

private sealed interface LinkState {
    data object Loading : LinkState
    data object Bare : LinkState
    data class Article(val metadata: LinkMetadata) : LinkState
}

/**
 * A saved link. Telegram supplies the article, so the device never calls the
 * site itself. A link with nothing to show stays a plain open action rather
 * than an empty card.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SavedLink(
    url: String,
    onOpen: () -> Unit,
    loadMetadata: suspend (String) -> LinkMetadata?,
    modifier: Modifier = Modifier,
    textScale: Float = 1f
) {
    val state by produceState<LinkState>(initialValue = LinkState.Loading, url) {
        val metadata = loadMetadata(url)
        value = when {
            metadata == null -> LinkState.Bare
            metadata.title == null &&
                    metadata.description == null &&
                    metadata.imagePath == null -> LinkState.Bare

            else -> LinkState.Article(metadata)
        }
    }

    when (val current = state) {
        LinkState.Loading -> Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(SPINNER_SIZE),
                strokeWidth = SPINNER_STROKE
            )
        }

        LinkState.Bare -> BareLink(url, onOpen, textScale, modifier)
        is LinkState.Article -> ArticleCard(url, current.metadata, onOpen, textScale, modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BareLink(
    url: String,
    onOpen: () -> Unit,
    textScale: Float,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BARE_ICON_SIZE)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = url,
            style = MaterialTheme.typography.bodyLarge.scaledBy(textScale),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpen, shapes = ButtonDefaults.shapes()) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(BUTTON_ICON_SIZE)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.preview_open_link))
        }
    }
}

@Composable
private fun ArticleCard(
    url: String,
    metadata: LinkMetadata,
    onOpen: () -> Unit,
    textScale: Float,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        metadata.imagePath?.let { path ->
            AsyncImage(
                model = path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PREVIEW_ASPECT)
                    .clip(MaterialTheme.shapes.extraLarge)
            )
        }
        Column(modifier = Modifier.padding(16.dp)) {
            metadata.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.scaledBy(textScale),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = metadata.siteName ?: url,
                style = MaterialTheme.typography.bodySmall.scaledBy(textScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            metadata.description?.let { description ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.scaledBy(textScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private const val PREVIEW_ASPECT = 16f / 9f
private val SPINNER_SIZE = 22.dp
private val SPINNER_STROKE = 2.dp
private val BARE_ICON_SIZE = 48.dp
private val BUTTON_ICON_SIZE = 18.dp
