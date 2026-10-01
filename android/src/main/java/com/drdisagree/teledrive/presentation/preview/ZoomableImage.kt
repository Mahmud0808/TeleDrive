package com.drdisagree.teledrive.presentation.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.preview_image_undecodable
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import org.jetbrains.compose.resources.stringResource

/**
 * Tiles are decoded at the current zoom, so a photo stays sharp without holding the whole image in
 * memory.
 */
@Composable
fun ZoomableImage(
    model: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    var undecodable by remember(model) { mutableStateOf(false) }
    if (undecodable) {
        EmptyState(
            icon = Icons.Filled.BrokenImage,
            title = stringResource(Res.string.preview_image_undecodable),
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val context = LocalContext.current
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .listener(onError = { _, _ -> undecodable = true })
            .build()
    }

    ZoomableAsyncImage(
        model = request,
        contentDescription = contentDescription,
        state = rememberZoomableImageState(
            rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = MAX_ZOOM))
        ),
        onClick = { onTap() },
        modifier = modifier.fillMaxSize()
    )
}

private const val MAX_ZOOM = 6f
